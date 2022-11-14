package fun.pinger;

import fun.pinger.config.Application;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author : P1n93r
 * @date : 2022/4/7 16:01
 */
@Slf4j
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LOGGER.info("[!] start scan...");
        Application.run(args);
    }

}
