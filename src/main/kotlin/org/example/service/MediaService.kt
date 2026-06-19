//22411895 김창민

package org.example.service

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.example.domain.Media
import org.example.repository.MediaRepository
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import java.io.File

/**
 * [미디어 메타데이터 업로드 등록 판단 인터페이스]
 * 업로드 루틴 통과 여부 및 거절 원인 규명을 유연하게 구현하기 위한 봉인 상태 선언부임.
 */
sealed interface SaveMediaResult {
    /** 업로드 저장이 완수되었음을 표명함 */
    data class Success(val media: Media) : SaveMediaResult
    /** 사용자가 등록하고자 하는 이름이 이미 등록되어 있어 반려 처리됨 */
    object DuplicateTitle : SaveMediaResult
}

/**
 * [동영상 제목 수정 분기 표식 상태형 코드]
 */
enum class UpdateMediaResult {
    /** 갱신 처리 완료 */
    SUCCESS,
    /** 수정 자격을 가지지 못한 비정상 경로 시도 */
    FORBIDDEN,
    /** 변경할 자원이 없음 */
    NOT_FOUND
}

/**
 * [미디어 파일 저장 가치 분석 및 물리 가상 소멸을 담당하는 서비스]
 * 로컬 하드 용량 보존 기법, 메타 무결성을 확보함.
 */
@Service
class MediaService(private val mediaRepository: MediaRepository) {

    /** 미디어 컨텐츠가 정적 물리 형태로 기밀 보관되는 디렉토리 상위 타겟임 */
    private val uploadDir = File("uploads").apply { if (!exists()) mkdirs() }

    /**
     * [미디어 파일 영구 업로드 처리]
     * 미디어가 타당한지 DB 검사를 수반한 후 충돌을 피해 드라이브 저장 장치에 보존함.
     *
     * @param title 업로드할 비디오가 브라우저에 투사할 타이틀 정보임.
     * @param fileType 비디오(VIDEO) 혹은 오디오(AUDIO) 분류 코드임.
     * @param filePart 스트림 버퍼 형태로 유입된 바이너리 멀티파트 구조물임.
     * @param uploadedBy 소유자로 영구 등록할 작성 유저 계정명임.
     */
    suspend fun saveMedia(title: String, fileType: String, filePart: FilePart, uploadedBy: String): SaveMediaResult {
        // [중복 검증 장치] 동명의 컨텐츠 등록을 엄격히 방어함
        val existing = mediaRepository.findByTitle(title)
        if (existing != null) {
            return SaveMediaResult.DuplicateTitle
        }

        // [물리 충돌 방지 로직] 유일한 인덱싱을 담보하기 위해 시스템 밀리초 값을 난수처럼 부착함
        val uniqueFileName = "${System.currentTimeMillis()}_${filePart.filename()}"
        val destination = File(uploadDir, uniqueFileName)

        // 리액터 퍼블리셔 기반의 Multipart 전송 연산을 코루틴 흐름으로 비차단 처리 대기함
        filePart.transferTo(destination).awaitSingleOrNull()

        val media = Media(
            title = title,
            fileType = fileType,
            fileName = uniqueFileName,
            contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream",
            uploadedBy = uploadedBy
        )
        val saved = mediaRepository.save(media)
        return SaveMediaResult.Success(saved)
    }

    /**
     * [업로드된 전적 미디어 컬렉션 목록 리스트 획득]
     */
    suspend fun getAllMedia(): List<Media> = mediaRepository.findAll().toList()

    /**
     * [미디어 단건 고유정보 조회]
     */
    suspend fun getMediaById(id: Long): Media? = mediaRepository.findById(id)

    /**
     * [미디어 타이틀 수정 변경 관리]
     * 작성 주주 본인 혹은 admin 만이 타이틀을 개명할 수 있도록 인가를 검증함.
     */
    suspend fun updateMedia(id: Long, newTitle: String, requestor: String): UpdateMediaResult {
        val media = mediaRepository.findById(id) ?: return UpdateMediaResult.NOT_FOUND

        if (requestor != "admin" && media.uploadedBy != requestor) {
            return UpdateMediaResult.FORBIDDEN
        }

        val updatedMedia = media.copy(title = newTitle)
        mediaRepository.save(updatedMedia)
        return UpdateMediaResult.SUCCESS
    }

    /**
     * [미디어 디스크 물리 보관 소멸 및 DB 인덱스 파괴 연산]
     */
    suspend fun deleteMedia(id: Long, requestor: String): DeleteResult {
        val media = mediaRepository.findById(id) ?: return DeleteResult.NOT_FOUND

        if (requestor != "admin" && media.uploadedBy != requestor) {
            return DeleteResult.FORBIDDEN
        }

        // 드라이브에 기 보관된 잔여 파일 데이터 제거
        val file = File(uploadDir, media.fileName)
        if (file.exists()) file.delete()

        // DB 테이블에 할당되어 있던 컬럼 내역 제거
        mediaRepository.deleteById(id)
        return DeleteResult.SUCCESS
    }

    /**
     * [미디어 파괴 시퀀스 이넘 상태 판단 코드]
     */
    enum class DeleteResult {
        SUCCESS, FORBIDDEN, NOT_FOUND
    }
}