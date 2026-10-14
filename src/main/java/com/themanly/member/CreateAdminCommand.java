package com.themanly.member;

import com.themanly.auth.PasswordResetService;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 관리자 생성 명령.
 *
 * <pre>
 *   # 운영 서버 (비밀번호 설정 링크가 나온다)
 *   docker compose -f docker-compose.prod.yml run --rm api create-admin --login-id=themanly --name=운영자 --role=SUPER_ADMIN
 *   # 로컬 — 아이디 + 임시 비밀번호 (local 프로필에서만)
 *   ./gradlew bootRun --args="create-admin --login-id=masteradmin --temp-password=… --role=SUPER_ADMIN"
 *   # 이메일로
 *   ./gradlew bootRun --args="create-admin --email=me@example.com --name=이름 --role=SUPER_ADMIN"
 * </pre>
 * SUPER_ADMIN 을 만드는 유일한 경로다.
 */
@Component
public class CreateAdminCommand implements ApplicationRunner {

    public static final String COMMAND = "create-admin";

    private final AdminMemberService adminMembers;
    private final PasswordResetService passwordReset;
    private final Environment environment;

    public CreateAdminCommand(AdminMemberService adminMembers, PasswordResetService passwordReset, Environment environment) {
        this.adminMembers = adminMembers;
        this.passwordReset = passwordReset;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains(COMMAND)) {
            return;
        }
        MemberRole role = MemberRole.valueOf(required(args, "role"));
        String name = args.containsOption("name") ? required(args, "name") : "관리자";
        if (args.containsOption("login-id")) {
            String temporary = null;
            if (args.containsOption("temp-password")) {
                if (!environment.matchesProfiles("local")) {
                    throw new IllegalStateException("--temp-password 는 local 프로필에서만 쓸 수 있습니다. 운영에서는 빼고 실행하면 설정 링크가 나옵니다.");
                }
                temporary = required(args, "temp-password");
            }
            String email = args.containsOption("email") ? required(args, "email") : null;
            AdminMemberService.CommandResult result =
                    adminMembers.createWithLoginId(required(args, "login-id"), email, name, role, temporary);
            // 로그가 아니라 표준 출력이다 — 링크는 이 터미널에만 남아야 한다
            System.out.println();
            System.out.println((result.created() ? "관리자 계정을 만들었습니다" : "기존 계정을 바꿨습니다")
                    + " (아이디 " + result.member().getLoginId() + ", " + role + ").");
            if (temporary != null) {
                System.out.println("/admin/login 에서 로그인한 뒤 비밀번호를 바꿔야 관리자 화면이 열립니다.");
            } else {
                System.out.println("아래 링크에서 비밀번호를 정해 주세요. 30분 동안 한 번만 쓸 수 있습니다:");
                System.out.println(passwordReset.issueLink(result.member()));
            }
            System.out.println();
            return;
        }
        AdminMemberService.CommandResult result = adminMembers.createOrPromoteByCommand(required(args, "email"), name, role);
        System.out.println();
        if (result.created()) {
            System.out.println("관리자 계정을 만들었습니다 (" + role + "). 아래 링크에서 비밀번호를 정해 주세요 (30분, 한 번):");
            System.out.println(passwordReset.issueLink(result.member()));
        } else {
            System.out.println("기존 계정의 역할을 " + role + " 로 바꿨습니다. 비밀번호는 그대로이고 세션은 끊었습니다.");
        }
        System.out.println();
    }

    private static String required(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("--" + name + " 값이 필요합니다.");
        }
        return values.getFirst().trim();
    }
}
