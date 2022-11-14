package fun.pinger.utils;

import fun.pinger.model.CustomSink;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : P1n93r
 * @date : 2022/4/10 18:31
 */
public class SinkUtil {

    public static List<CustomSink> loadSink(Path sinkPath){
        List<CustomSink> ret = null;
        try {
            ret = SinkUtil.loadSink(new FileInputStream(sinkPath.toFile()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return ret;
    }


    public static List<CustomSink> loadSink(InputStream inputStream){
        ArrayList<CustomSink> customSinks = new ArrayList<>();
        try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String c;
                if (!(c = line.split("#")[0].trim()).isEmpty()) {
                    String[] slinks = c.split(" ");
                    CustomSink customSink = new CustomSink();
                    if (slinks.length > 0) {
                        customSink.setClassName(slinks[0]);
                    }
                    if (slinks.length > 1) {
                        customSink.setMethod(slinks[1]);
                    }
                    if (slinks.length > 2) {
                        customSink.setDesc(slinks[2]);
                    }
                    if (slinks.length > 3) {
                        customSink.setTaintArg(Integer.parseInt(slinks[3]));
                    }
                    if (slinks.length > 4) {
                        customSink.setMatchSubClassMethod(Boolean.parseBoolean(slinks[4]));
                    }

                    String note = line.split("#")[1].trim();
                    if(!note.isEmpty()){
                        // 设置注释
                        customSink.setNote(note);
                    }

                    String type = line.split("#")[2].trim();
                    if(!type.isEmpty()){
                        // 设置sink的类型
                        customSink.setType(type);
                    }
                    customSinks.add(customSink);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return customSinks;
    }


}
