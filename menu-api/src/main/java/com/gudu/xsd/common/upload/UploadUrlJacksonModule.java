package com.gudu.xsd.common.upload;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;

/**
 * 上传地址签名的唯一接入点：只注册在 HTTP 消息转换器的 ObjectMapper 上（见 UploadAccessConfig），
 * 不影响入库 / Redis / 调模型用的 ObjectMapper。
 *
 * <ul>
 *   <li>序列化：任何 String 值（DTO 字段、Map 值、List 元素、SSE 事件数据）若是本站上传地址或逗号分隔的地址列表 → 签名；
 *       头像、菜谱图、帖子图、反馈图等各接口无需各自处理；</li>
 *   <li>反序列化：请求体里的本站上传地址 → 验签后剥掉签名还原规范地址（库里只存不带签名的地址）。</li>
 * </ul>
 */
public final class UploadUrlJacksonModule extends SimpleModule {

    public UploadUrlJacksonModule(UploadUrlSigner signer) {
        super("UploadUrlSigning");
        addSerializer(String.class, new SigningSerializer(signer));
        addDeserializer(String.class, new AcceptingDeserializer(signer));
    }

    static final class SigningSerializer extends StdScalarSerializer<String> {
        private final transient UploadUrlSigner signer;

        SigningSerializer(UploadUrlSigner signer) {
            super(String.class);
            this.signer = signer;
        }

        @Override
        public boolean isEmpty(SerializerProvider prov, String value) {
            return value.isEmpty();
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(signer.signAll(value));
        }
    }

    static final class AcceptingDeserializer extends StdScalarDeserializer<String> {
        private final transient UploadUrlSigner signer;

        AcceptingDeserializer(UploadUrlSigner signer) {
            super(String.class);
            this.signer = signer;
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String raw = StringDeserializer.instance.deserialize(p, ctxt);
            return signer.acceptAll(raw);
        }
    }
}
