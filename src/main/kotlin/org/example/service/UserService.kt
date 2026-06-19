//22411895 김창민
package org.example.service

import org.example.domain.User
import org.example.repository.UserRepository
import org.springframework.stereotype.Service

/**
 * [유저 핵심 비즈니스 연산 로직 서비스]
 * 회원 가입, 중복 검증, 수정 절차, 관리자/당사자 권한 분기 처리를 엄격히 주관함.
 */
@Service
class UserService(private val userRepository: UserRepository) {

    /**
     * [신규 회원 가입 수행]
     * 회원 가입 폼 데이터를 받아 R2DBC Repository를 거쳐 영구 등록 처리함.
     */
    suspend fun register(user: User): User = userRepository.save(user)

    /**
     * [아이디 기반 회원 조회]
     * 회원 도메인 고유 닉네임 키워드를 대조하여 상세 정보를 클라이언트에 송출함.
     */
    suspend fun getByUsername(username: String): User? = userRepository.findByUsername(username)

    /**
     * [유저 프로필 정보 갱신]
     * 회원 고유 DB 식별번호를 타겟으로 하여 새로운 명세 데이터로 오버라이드함.
     *
     * @param id 수정 대상 회원의 DB PK임.
     * @param updatedUser 새로 수정한 내용을 품고 있는 타겟 갱신 유저 인스턴스임.
     * @return 저장 완료된 회원 인스턴스 혹은 변경 타겟이 미실존할 시 null을 리턴함.
     */
    suspend fun updateUser(id: Long, updatedUser: User): User? {
        val existingUser = userRepository.findById(id) ?: return null
        val userToSave = existingUser.copy(
            username = updatedUser.username,
            email = updatedUser.email,
            password = updatedUser.password
        )
        return userRepository.save(userToSave)
    }

    /**
     * [회원 데이터 소멸/탈퇴 연산]
     * 본인 혹은 admin 권한 여부를 체크하여 보안 규격을 유지한 채 삭제 처리를 위임받음.
     *
     * @param id 강제 처분 혹은 탈퇴할 유저 고유 PK임.
     * @param requestor 삭제 액션을 도모한 현재 인증 세션의 가입 ID임.
     * @return 검증 결과 분기 코드를 반환하여 컨트롤러 측에서 알맞은 에러 헤더 응답을 작성하게 도움.
     */
    suspend fun deleteUser(id: Long, requestor: String): DeleteUserResult {
        // DB 내부에서 삭제 타겟 탐색 가드 조건
        val user = userRepository.findById(id) ?: return DeleteUserResult.NOT_FOUND

        // 관리자가 아니고 삭제를 당하는 가입자와 요청자가 다를 경우, 위조 행위로 간주해 접근 차단함
        if (requestor != "admin" && user.username != requestor) {
            return DeleteUserResult.FORBIDDEN
        }

        userRepository.deleteById(id)
        return DeleteUserResult.SUCCESS
    }

    /**
     * [회원 소멸 연산 결과를 명확히 식별하기 위한 분기 식별 열거형 코드]
     */
    enum class DeleteUserResult {
        /** 삭제 과정 전체가 오류 없이 정상 마무리됨 */
        SUCCESS,
        /** 계정 명의 불일치 혹은 관리자 표식 부재로 보안 규격이 차단됨 */
        FORBIDDEN,
        /** 삭제의 대상이 되는 회원이 서버 시스템 상에 실존하지 않음 */
        NOT_FOUND
    }
}