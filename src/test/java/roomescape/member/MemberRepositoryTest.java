package roomescape.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MemberRepositoryTest {
    private static final String EMAIL = "test@email.com";
    private static final String PASSWORD = "password";

    @Autowired
    private MemberRepository memberRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(new Member("테스터", EMAIL, PASSWORD, "USER"));
    }

    @Test
    void 이메일과_비밀번호가_일치하면_회원을_반환한다() {
        // when
        Optional<Member> result = memberRepository.findByEmailAndPassword(EMAIL, PASSWORD);

        // then
        assertThat(result).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(member.getId());
            assertThat(found.getName()).isEqualTo("테스터");
            assertThat(found.getEmail()).isEqualTo(EMAIL);
            assertThat(found.getRole()).isEqualTo("USER");
        });
    }

    @Test
    void 이메일이나_비밀번호가_일치하지_않으면_빈_결과를_반환한다() {
        // when
        Optional<Member> wrongPassword = memberRepository.findByEmailAndPassword(EMAIL, "wrong-password");
        Optional<Member> unknownEmail = memberRepository.findByEmailAndPassword("unknown@email.com", PASSWORD);

        // then
        assertThat(wrongPassword).isEmpty();
        assertThat(unknownEmail).isEmpty();
    }

    @Test
    void 회원_ID로_회원을_조회한다() {
        // when
        Optional<Member> result = memberRepository.findById(member.getId());

        // then
        assertThat(result).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(member.getId());
            assertThat(found.getName()).isEqualTo("테스터");
            assertThat(found.getEmail()).isEqualTo(EMAIL);
            assertThat(found.getRole()).isEqualTo("USER");
        });
    }

    @Test
    void 존재하지_않는_회원_ID를_조회하면_빈_결과를_반환한다() {
        // when
        Optional<Member> result = memberRepository.findById(-1L);

        // then
        assertThat(result).isEmpty();
    }

}
