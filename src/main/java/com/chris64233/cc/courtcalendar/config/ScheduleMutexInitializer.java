package com.chris64233.cc.courtcalendar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.courtcalendar.domain.ScheduleMutex;
import com.chris64233.cc.courtcalendar.repository.ScheduleMutexRepository;

/**
 * 应用启动后确保排期互斥锁单行存在。
 */
@Component
public class ScheduleMutexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleMutexInitializer.class);

    private final ScheduleMutexRepository mutexRepository;

    public ScheduleMutexInitializer(ScheduleMutexRepository mutexRepository) {
        this.mutexRepository = mutexRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mutexRepository.findById(ScheduleMutex.SINGLETON_ID).isEmpty()) {
            mutexRepository.save(new ScheduleMutex(ScheduleMutex.SINGLETON_ID));
            log.debug("已初始化排期互斥锁");
        }
    }
}
