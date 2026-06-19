//22411895 김창민
package org.example.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * [회원 가입 정보 엔티티]
 * 데이터베이스의 'users' 테이블과 대응되는 리액티브 영속화 전용 엔티티임.
 *
 * @property id DB 내에서 보존 관리되는 고유 식별 주소임. (Auto-increment PK)
 * @property username 중복이 불가능한 사용자 고유 아이디 명칭임.
 * @property email 통지 혹은 비밀번호 복구 프로세스 시 활용될 가입 이메일 주소임.
 * @property password 사용자의 신원을 인증하는 비밀번호 평문 스트링 값임.
 */
@Table("users")
data class User(
    @Id val id: Long? = null,
    val username: String,
    val email: String,
    val password: String
)