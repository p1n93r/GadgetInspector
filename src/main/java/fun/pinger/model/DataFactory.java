package fun.pinger.model;

/**
 * @author P1n93r
 */
public interface DataFactory<T> {
    T parse(String[] fields);
    String[] serialize(T obj);
}
