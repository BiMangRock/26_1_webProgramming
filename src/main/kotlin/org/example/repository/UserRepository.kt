//22411895 김창민
package org.example.repository

import org.example.domain.User
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/**
 * [유저 데이터베이스 인터페이스]
 * R2DBC 환경에서 동작하며 비동기 리액티브 데이터 입출력을 지원함.
 * CoroutineCrudRepository 상속을 통해 논블로킹 상태로 데이터 작업을 실행하도록 강제함.
 */
interface UserRepository : CoroutineCrudRepository<User, Long> {

    /**
     * [아이디를 통한 단건 조회 기능]
     * 전달받은 사용자 고유명(Username)에 대응하는 유저 레코드를 탐색함.
     * 코루틴 환경을 기반으로 하므로 suspend 지연 연산자를 부착함.
     *
     * @param username 조회할 회원 명칭임.
     * @return DB에 데이터가 존재할 시 User 객체를, 없을 시 null을 최종 반환함.
     */
    suspend fun findByUsername(username: String): User?
}