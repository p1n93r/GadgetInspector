package fun.pinger.source.javaserial;

import fun.pinger.core.SerializableDecider;
import fun.pinger.discovery.GadgetChainDiscovery;
import fun.pinger.model.ClassReference;
import fun.pinger.model.CustomSink;
import fun.pinger.model.InheritanceMap;
import fun.pinger.utils.SinkUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author P1n93r
 */
public class JavaSerializableDecider implements SerializableDecider {
    private final Map<ClassReference.Handle, Boolean> cache = new HashMap<>();

    private static final Map<String,Boolean> BLACK_LIST = new HashMap<>();

    static {
        InputStream inputStream = JavaSerializableDecider.class.getResourceAsStream("/blacklist.txt");
        try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] split = line.split(" ");
                BLACK_LIST.put(split[0],Boolean.valueOf(split[1]));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private final InheritanceMap inheritanceMap;

    public JavaSerializableDecider(InheritanceMap inheritanceMap) {
        this.inheritanceMap = inheritanceMap;
    }

    /**
     * 用于判断class是否可以被序列化
     */
    @Override
    public Boolean apply(ClassReference.Handle handle) {
        Boolean cached = cache.get(handle);
        if (cached != null) {
            return cached;
        }

        Boolean result = applyNoCache(handle);

        cache.put(handle, result);
        return result;
    }

    private Boolean applyNoCache(ClassReference.Handle handle) {

        if (isBlacklistedClass(handle)) {
            return false;
        }

        // 判断是否有直接或间接实现java/io/Serializable序列化接口
        if (inheritanceMap.isSubclassOf(handle, new ClassReference.Handle("java/io/Serializable"))) {
            return true;
        }

        return false;
    }

    /**
     * 判断class是否在黑名单内，这些黑名单就代表一定不能序列化
     */
    private static boolean isBlacklistedClass(ClassReference.Handle clazz) {
        String clazzName = clazz.getName();
        for (Map.Entry<String, Boolean> entry : BLACK_LIST.entrySet()) {
            String className = entry.getKey();
            Boolean isStrict = entry.getValue();
            if(isStrict && clazzName.equals(className)){
                return true;
            }else if(!isStrict && clazzName.startsWith(className)){
                return true;
            }
        }
        return false;
    }
}
