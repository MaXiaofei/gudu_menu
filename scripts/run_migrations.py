#!/usr/bin/env python3
"""通过 HTTP CONNECT 代理（7897）执行 SQL 迁移到远程 MySQL（staging 49.232.3.201:13306）。
实现：本地 33306 端口 → CONNECT 隧道 → 双向转发 → pymysql 连本地端口。
用法: python3 run_migrations.py V42__ingredient_stock.sql [V43__*.sql ...]
"""
import socket
import select
import threading
import sys
import os

PROXY_HOST, PROXY_PORT = "127.0.0.1", 7897
DB_HOST, DB_PORT = "49.232.3.201", 13306
LOCAL_PORT = 33306
DB_USER = "root"
DB_PASS = os.environ.get("MYSQL_ROOT_PASSWORD", "")
DB_NAME = "gudu"
SQL_DIR = "/Users/maxiaofei/mygithub/menu-new/menu-api/sql"


def make_tunnel():
    s = socket.create_connection((PROXY_HOST, PROXY_PORT), timeout=15)
    s.sendall(f"CONNECT {DB_HOST}:{DB_PORT} HTTP/1.1\r\nHost: {DB_HOST}:{DB_PORT}\r\n\r\n".encode())
    resp = b""
    while b"\r\n\r\n" not in resp:
        chunk = s.recv(4096)
        if not chunk:
            raise RuntimeError("代理无响应")
        resp += chunk
    status = resp.split(b"\r\n", 1)[0].decode()
    if " 200 " not in status:
        raise RuntimeError(f"CONNECT 失败: {status}")
    return s


def pump(a, b, stop):
    while not stop.is_set():
        r, _, _ = select.select([a, b], [], [], 0.2)
        for src in r:
            try:
                d = src.recv(65536)
            except OSError:
                stop.set()
                return
            if not d:
                stop.set()
                return
            dst = b if src is a else a
            dst.sendall(d)


def split_statements(sql):
    lines = [l for l in sql.splitlines() if not l.strip().startswith("--") and l.strip()]
    return [s.strip() for s in "\n".join(lines).split(";") if s.strip()]


def main():
    import pymysql

    files = sys.argv[1:]
    ls = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    ls.bind(("127.0.0.1", LOCAL_PORT))
    ls.listen(1)
    print(f"隧道: 127.0.0.1:{LOCAL_PORT} → {DB_HOST}:{DB_PORT} (代理 {PROXY_HOST}:{PROXY_PORT})")

    conn_sock, _ = ls.accept()
    tunnel = make_tunnel()
    stop = threading.Event()
    threading.Thread(target=pump, args=(conn_sock, tunnel, stop), daemon=True).start()
    threading.Thread(target=pump, args=(tunnel, conn_sock, stop), daemon=True).start()

    conn = pymysql.connect(host="127.0.0.1", port=LOCAL_PORT, user=DB_USER,
                           password=DB_PASS, database=DB_NAME, charset="utf8mb4",
                           autocommit=True, connect_timeout=15)
    cur = conn.cursor()
    for f in files:
        path = os.path.join(SQL_DIR, f)
        with open(path, encoding="utf-8") as fh:
            stmts = split_statements(fh.read())
        ok = skip = 0
        for stmt in stmts:
            try:
                cur.execute(stmt)
                ok += 1
            except Exception as e:
                msg = str(e)
                if "already exists" in msg or "Duplicate column" in msg or "Duplicate key name" in msg:
                    ok += 1
                    continue
                print(f"  [跳过] {msg[:100]}")
                skip += 1
        print(f"{f}: {ok} 成功, {skip} 跳过")
    conn.close()
    stop.set()
    print("完成")


if __name__ == "__main__":
    main()
