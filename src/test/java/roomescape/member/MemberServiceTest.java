package roomescape.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import roomescape.member.exception.MemberErrorCode;
import roomescape.member.exception.MemberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(MemberService.class)
class MemberServiceTest {
    private static final String EMAIL = "test@email.com";
    private static final String PASSWORD = "password";
    private Long memberId;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(new Member("테스터", EMAIL, PASSWORD, "USER"));
        memberId = member.getId();
    }

    @Test
    void 로그인_정보가_일치하면_회원_정보를_반환한다() {
        // when
        Member result = memberService.login(EMAIL, PASSWORD);

        // then
        assertThat(result.getId()).isEqualTo(memberId);
        assertThat(result.getName()).isEqualTo("테스터");
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getRole()).isEqualTo("USER");
    }

    @Test
    void 일치하는_회원이_없으면_로그인_실패_예외를_던진다() {
        // given
        String wrongPassword = "wrong-password";

        // when & then
        assertThatThrownBy(() -> memberService.login(EMAIL, wrongPassword))
                .isInstanceOfSatisfying(MemberException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED));
    }

}
