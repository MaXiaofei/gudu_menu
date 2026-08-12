#!/usr/bin/env python3
"""HTTP CONNECT 隧道转发器：本地 33306 → 49.232.3.201:13306（走 7897 代理）。
用法: python3 db_tunnel.py  （常驻，Ctrl+C 退出）
"""
import socket
import select
import threading
import sys

PROXY_HOST, PROXY_PORT = "127.0.0.1", 7897
DB_HOST, DB_PORT = "49.232.3.201", 13306
LOCAL_PORT = 33306


def make_tunnel():
    s = socket.create_connection((PROXY_HOST, PROXY_PORT), timeout=15)
    s.sendall(f"CONNECT {DB_HOST}:{DB_PORT} HTTP/1.1\r\nHost: {DB_HOST}:{DB_PORT}\r\n\r\n".encode())
    s.settimeout(15)
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = s.recv(4096)
        if not chunk:
            raise RuntimeError("代理无响应")
        buf += chunk
    if b" 200 " not in buf.split(b"\r\n", 1)[0]:
        raise RuntimeError("CONNECT 被拒")
    # P3-10 修复：CONNECT 响应头后 recv 可能多读应用层数据（如 MySQL greeting），
    # 必须把 leftover 回传客户端，否则握手包丢失、pymysql 卡死在等 greeting。
    leftover = buf.split(b"\r\n\r\n", 1)[1] if b"\r\n\r\n" in buf else b""
    s.settimeout(None)
    return s, leftover


def pump(a, b):
    """单线程双向转发（避免多线程 recv 同一 socket 竞争错乱）。"""
    while True:
        try:
            r, _, _ = select.select([a, b], [], [], 1.0)
        except (OSError, ValueError):
            return
        for src in r:
            try:
                d = src.recv(65536)
            except OSError:
                return
            if not d:
                return
            try:
                (b if src is a else a).sendall(d)
            except OSError:
                return


def main():
    ls = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    ls.bind(("127.0.0.1", LOCAL_PORT))
    ls.listen(5)
    print(f"隧道就绪: 127.0.0.1:{LOCAL_PORT} → {DB_HOST}:{DB_PORT}", flush=True)
    while True:
        conn, _ = ls.accept()
        try:
            tunnel, leftover = make_tunnel()
        except Exception as e:
            print(f"隧道建立失败: {e}", flush=True)
            conn.close()
            continue
        # P3-10：CONNECT 响应后多读的应用层数据先回传客户端（关键：否则 MySQL greeting 丢失）
        if leftover:
            try:
                conn.sendall(leftover)
            except OSError:
                conn.close()
                tunnel.close()
                continue
        threading.Thread(target=pump, args=(conn, tunnel), daemon=True).start()
        print("新连接已转发", flush=True)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("退出", flush=True)
