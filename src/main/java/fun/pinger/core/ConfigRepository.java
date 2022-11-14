package fun.pinger.core;

import fun.pinger.source.javaserial.JavaDeserializationConfig;
import fun.pinger.source.servlet.HttpServletRequestSourceConfig;
import fun.pinger.source.springMVC.SpringMVCSourceConfig;
import fun.pinger.source.struts.StrutsSourceConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author P1n93r
 */
public class ConfigRepository {
    public static final List<ScanTypeConfig> ALL_CONFIGS = Collections.unmodifiableList(Arrays.asList(
            new JavaDeserializationConfig(),
            new HttpServletRequestSourceConfig(),
            new SpringMVCSourceConfig(),
            new StrutsSourceConfig()
    ));

    /**
     * 根据扫描类型名获取对应的ScanTypeConfig
     * @param name 扫描类型名
     * @return 对应的ScanTypeConfig
     */
    public static ScanTypeConfig getConfig(String name) {
        for (ScanTypeConfig config : ALL_CONFIGS) {
            if (config.getName().equals(name)) {
                return config;
            }
        }
        return null;
    }
}
