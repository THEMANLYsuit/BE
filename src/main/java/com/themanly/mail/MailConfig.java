package com.themanly.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 발송.
 *
 * <p>{@code spring.mail.host} 가 없으면 {@link JavaMailSender} 빈이 없다 — 그때는 보내지 않고 경고만 남긴다.
 * 보낼 때는 가상 스레드에서 보낸다. 요청 처리가 SMTP 응답을 기다리지 않게.
 * 실패해도 요청은 성공한다 — 예약은 됐는데 메일만 안 간 것이고, 로그로 본다.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public Mailer mailer(ObjectProvider<JavaMailSender> senderProvider, @Value("${app.mail.from}") String from) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("메일 설정(spring.mail.host)이 없습니다. 메일을 보내지 않습니다.");
            return (to, subject, text) -> log.warn("메일 미발송(설정 없음) subject={}", subject);
        }
        return (to, subject, text) -> Thread.ofVirtual().name("mail").start(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            try {
                sender.send(message);
                log.info("메일 발송 subject={}", subject);
            } catch (MailException e) {
                log.error("메일 발송 실패 subject={}", subject, e);
            }
        });
    }
}
