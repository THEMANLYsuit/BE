package com.themanly.mail;

/** 메일 한 통. 구현은 {@link MailConfig} — SMTP 설정이 없으면 보내지 않고 경고만 남긴다. */
public interface Mailer {
    void send(String to, String subject, String text);
}
