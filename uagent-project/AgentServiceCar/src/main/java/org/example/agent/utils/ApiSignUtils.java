package org.example.agent.utils;


import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class ApiSignUtils {
    public static String sign(String secretKey, Long timestamp, Map<String, Object> params) throws Exception {
        //组装参数
        StringBuffer message = new StringBuffer();
        message.append(secretKey);
        //先拿到keys
        Set<String> keys = params.keySet();
        Object[] keyz = keys.toArray();
        //排序
        Arrays.sort(keyz);
        for (Object key : keyz) {
            if (key.toString().startsWith("_")) {
                continue;
            }
            message.append((String) key);
            message.append(params.get((String) key));
        }
        message.append("_timestamp").append(timestamp);
        message.append(secretKey);
        //MD5签名
        return getMD5(message.toString());
    }

    public static String getMD5(String message) throws Exception {
        MessageDigest messageDigest = null;
        StringBuffer md5StrBuff = new StringBuffer();
        messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.reset();
        messageDigest.update(message.getBytes("UTF-8"));

        byte[] byteArray = messageDigest.digest();
        for (int i = 0; i < byteArray.length; i++) {
            if (Integer.toHexString(0xFF & byteArray[i]).length() == 1)
                md5StrBuff.append("0").append(Integer.toHexString(0xFF & byteArray[i]));
            else
                md5StrBuff.append(Integer.toHexString(0xFF & byteArray[i]));
        }
        return md5StrBuff.toString().toUpperCase();//字母大写
    }
}
