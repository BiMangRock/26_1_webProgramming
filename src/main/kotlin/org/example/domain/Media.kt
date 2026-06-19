//22411895 김창민
package org.example.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * [미디어 자원 엔티티]
 * 업로드된 비디오 및 오디오 메타데이터를 저장하기 위한 R2DBC 전용 데이터 모델임.
 *
 * @property id 개별 파일 정보들을 고유 식별하기 위한 자동 증가 기본키(PK) 식별자임.
 * @property title 브라우저 클라이언트에 노출될 고유 동영상 제목(중복 금지 규칙 적용 대상)임.
 * @property fileType 동영상의 성격을 명시함 (VIDEO 혹은 AUDIO 지정).
 * @property fileName 로컬 하드 디스크에 최종 기록된 물리적 파일 경로명(Timestamp 결합)임.
 * @property contentType 미디어 스트리밍 요청 시 전송할 HTTP 응답 헤더의 MIME 타입 정보임.
 * @property uploadedBy 해당 미디어를 저장 요청한 최초 게시 유저의 username 임.
 */
@Table("media")
data class Media(
    @Id val id: Long? = null,
    val title: String,
    val fileType: String,
    val fileName: String,
    val contentType: String,
    val uploadedBy: String
)