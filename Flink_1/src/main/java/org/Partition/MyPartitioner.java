package org.Partition;

import org.apache.flink.api.common.functions.Partitioner;

//自定义分区，奇数一个分区，偶数一个分区
public class MyPartitioner implements Partitioner<String> {
    @Override
    public int partition(String key, int numPartitions) {
        return Integer.parseInt(key) % numPartitions;
    }
}
