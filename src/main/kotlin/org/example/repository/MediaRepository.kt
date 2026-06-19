//22411895 김창민
package org.example.repository

import org.example.domain.Media
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/**
 * [미디어 메타데이터 데이터베이스 조작 인터페이스]
 * Spring Data Coroutine R2DBC를 통해 영속 컨텍스트를 다룸.
 * 지연 스레드 점유 없이 안전한 파일 저장 프로세스를 구현하기 위해 사용됨.
 */
interface MediaRepository : CoroutineCrudRepository<Media, Long> {

    /**
     * [제목 중복 검사 전용 조회 메소드]
     * 인자로 전달된 미디어 제목과 동명의 메타 정보가 존재하는지 식별함.
     *
     * @param title 사용자가 업로드 시 지정을 시도한 임의의 영상 제목임.
     * @return 이미 동명의 영상이 DB에 저장되어 있다면 Media 개체를, 그렇지 않다면 null을 반환함.
     */
    suspend fun findByTitle(title: String): Media?
}