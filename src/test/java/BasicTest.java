//import io.leego.banana.BananaUtils;
//import io.leego.banana.Font;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterDescription;
import fun.pinger.config.Command;
import fun.pinger.core.ConfigRepository;
import fun.pinger.model.CustomSink;
import fun.pinger.utils.ClassLoaderUtil;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import org.junit.Test;
import org.objectweb.asm.Type;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.ResourceBundle;

/**
 * @author : P1n93r
 * @date : 2022/4/8 19:24
 */
public class BasicTest {

    @Test
    public void testFileWrite()throws Exception{
        try(
            // 准备写入到结果文件
            OutputStream outputStream = Files.newOutputStream(Paths.get("gadget-chains.txt"));
            Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        ){
            writer.write("fuck.....");
        }
    }

    @Test
    public void testAsciiArt()throws Exception{
//        String bananaify = BananaUtils.bananaify("XXXX", Font.ANSI_SHADOW);
//        System.out.println(bananaify);
    }


    @Test
    public void testLoadSinks()throws Exception{
        ArrayList<CustomSink> customSinks = new ArrayList<>();
        try(BufferedReader bufferedReader = Files.newBufferedReader(Paths.get("src/main/resources/common-sinks.txt"))) {
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
        System.out.println(String.format("[*] all sinks are %d",customSinks.size()));
        for (CustomSink customSink : customSinks) {
            // 遍历每个sink的参数，看下哪个参数落了写了
            Class<? extends CustomSink> clazz = customSink.getClass();
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field currentField = fields[i];
                currentField.setAccessible(true);
                Object tmp = currentField.get(customSink);
                if (tmp instanceof String && "".equals(tmp)){
                    System.out.println("=====================");
                    System.out.println(customSink);
                    System.out.println("=====================");
                }
                if(currentField.getName().equals("taintArg")){
                    System.out.println(tmp);
                }
            }
        }
    }

    @Test
    public void testAndOp()throws Exception{
        boolean test = false;
        test &= true;
        System.out.println(test);
    }

    @Test
    public void testClassLoaderUtil()throws Exception{

        Path classPath = Paths.get("");
        Path jarPath = Paths.get("");
        ClassLoader classLoader = ClassLoaderUtil.getCustomClassLoader(classPath,jarPath);

        ImmutableSet<ClassPath.ClassInfo> allClasses = ClassPath.from(classLoader).getAllClasses();
        System.out.println(allClasses.size());

    }



    @Test
    public void testReplaceBytecode()throws Exception{
        String ins = "";
        String safeIns = ins.replaceAll("[\\x00-\\x08\\x0b-\\x0c\\x0e-\\x1f]", "");
    }


    @Test
    public void testGetAllScanType()throws Exception{
        System.out.println(ConfigRepository.ALL_CONFIGS);

        Command command = Command.getInstance();
        JCommander jc = JCommander.newBuilder().addObject(command).build();
        jc.parse(new String[]{});

        jc.setDescriptionsBundle(new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                return null;
            }

            @Override
            public Enumeration<String> getKeys() {
                return null;
            }
        });


        for (ParameterDescription parameter : jc.getParameters()) {
            if(parameter.getNames().equals("--type")){
                System.out.println(parameter.getDescription());
            }
        }

    }


    @Test
    public void testAsm()throws Exception{
        String desc = "(Ljava/lang/String;)Ljava/lang/StringBuffer;";
        int size = Type.getReturnType(desc).getSize();
        System.out.println(size);
    }







}




