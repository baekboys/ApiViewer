package com.baek.viewer;

import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.*;

/**
 * SMTP 연결 테스트 (독립 실행).
 * Spring Boot 없이 단독으로 실행하여 SMTP 서버 접속을 확인합니다.
 *
 * 실행: java -cp target/classes:lib/* com.baek.viewer.SmtpTest
 * 또는 IDE에서 직접 Run
 */
public class SmtpTest {

    // ── 여기만 수정 ──────────────────────────────
    static final String SMTP_HOST = "smtp.company.com";
    static final int    SMTP_PORT = 25;        // 25 or 587
    static final String SMTP_USER = "";        // 인증 없으면 빈값
    static final String SMTP_PASS = "";
    static final String FROM      = "apiviewer@company.com";
    static final String TO        = "admin@company.com";
    // ─────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=== SMTP 연결 테스트 ===");
        System.out.printf("서버: %s:%d%n", SMTP_HOST, SMTP_PORT);
        System.out.printf("발신: %s → 수신: %s%n", FROM, TO);

        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", String.valueOf(SMTP_PORT));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        if (SMTP_PORT == 587) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.auth", "true");
        }

        Session session;
        if (SMTP_USER != null && !SMTP_USER.isEmpty()) {
            props.put("mail.smtp.auth", "true");
            session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SMTP_USER, SMTP_PASS);
                }
            });
        } else {
            session = Session.getInstance(props);
        }
        session.setDebug(true); // SMTP 프로토콜 상세 로그

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(FROM));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO));
            msg.setSubject("[URL Viewer] SMTP 테스트");
            msg.setText("이 메일이 수신되면 SMTP 설정이 정상입니다.\n\n" +
                        "서버: " + SMTP_HOST + ":" + SMTP_PORT);

            System.out.println("\n발송 중...");
            Transport.send(msg);
            System.out.println("\n✅ 발송 성공!");
        } catch (Exception e) {
            System.out.println("\n❌ 발송 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
