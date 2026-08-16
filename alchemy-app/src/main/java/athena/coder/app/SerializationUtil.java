package athena.coder.app;

import athena.coder.exception.RocAgentException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

/**
 * Java 原生序列化工具：ObjectOutputStream/ObjectInputStream + Base64 编码
 */
public final class SerializationUtil {

    private SerializationUtil() {
    }

    /**
     * 将对象序列化为 Base64 编码的字符串
     */
    public static <T> String serializeToString(T obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            throw new RocAgentException("序列化失败", e);
        }
    }

    /**
     * 从 Base64 编码的字符串反序列化为指定类型对象
     */
    public static <T> T deserializeFromString(String str, Class<T> clazz) {
        return clazz.cast(deserializeFromString(str));
    }

    private static Object deserializeFromString(String str) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(str));
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new RocAgentException("反序列化失败", e);
        }
    }
}
