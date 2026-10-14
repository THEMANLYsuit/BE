package com.themanly;

import com.themanly.member.CreateAdminCommand;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * THE MANLY 맞춤정장 API.
 *
 * <p>레오네페리토 API 와 같은 틀(Spring Boot 4.1 · Java 21 · PostgreSQL 18 · 세션 쿠키)이다.
 * 다른 점은 파는 방식 — 기성복 주문 · 결제 대신 <b>상담 예약 → 치수 → 맞춤 주문 진행</b>이다.
 */
@SpringBootApplication
public class ThemanlyApiApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(CreateAdminCommand.COMMAND)) {
            /*
             * 관리자 생성 명령. 앱 전체(세션 저장소 · 메일 포함)가 떠야 동작하므로 웹 서버까지 띄우되,
             * 임의 포트(0)로 띄워 이미 돌고 있는 서버와 부딪히지 않게 한다. 명령이 끝나면 내린다.
             */
            String[] withRandomPort = Arrays.asList(args).contains("--server.port=0")
                    ? args
                    : Stream.concat(Arrays.stream(args), Stream.of("--server.port=0")).toArray(String[]::new);
            ConfigurableApplicationContext context = SpringApplication.run(ThemanlyApiApplication.class, withRandomPort);
            System.exit(SpringApplication.exit(context));
        }
        SpringApplication.run(ThemanlyApiApplication.class, args);
    }
}
