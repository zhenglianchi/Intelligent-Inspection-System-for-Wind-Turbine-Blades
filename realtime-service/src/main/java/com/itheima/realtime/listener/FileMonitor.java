package com.itheima.realtime.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 文件监控器
 * 监控FTP目录下新增的txt音频文件，当有新文件上传时自动更新缓存
 *
 * @Migration migrated from wtb-health-monitor
 * @Modified 缓存改为RedisCacheService
 */
@Slf4j
@Component
public class FileMonitor {

    /**
     * 监控目录的根路径（从配置读取）
     */
    @Value("${realtime.ftp.root-dir}")
    String rootDir;

    /**
     * 项目启动后初始化文件监控
     * 轮询间隔5秒
     */
    /*@PostConstruct
    public void initFileMonitor() {
        // 轮询间隔 5 秒
        long interval = TimeUnit.SECONDS.toMillis(5);

        // 创建一个文件观察器，只监控txt结尾的数据文件
        FileAlterationObserver observer = new FileAlterationObserver(
                rootDir,
                FileFilterUtils.and(
                        FileFilterUtils.fileFileFilter(),
                        FileFilterUtils.suffixFileFilter(".txt")
                )
        );

        // 添加文件变化监听器
        observer.addListener(new FileListener());

        // 创建文件变化监控器并启动
        FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);
        try {
            monitor.start();
            log.info("✅ [FileMonitor] 文件监控启动，监控目录: {}", rootDir);
        } catch (Exception e) {
            log.error("❌ [FileMonitor] 文件监控启动失败: {}", e.getMessage(), e);
        }
    }*/
}
