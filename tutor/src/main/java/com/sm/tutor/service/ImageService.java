package com.sm.tutor.service;

import com.sm.tutor.domain.Lecture;
import com.sm.tutor.domain.LectureImage;
import com.sm.tutor.domain.Member;
import com.sm.tutor.repository.LectureRepository;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
  private final S3Service s3Service;
  private final MemberService memberService;
  private final LectureRepository lectureRepository;
  private final LectureService lectureService;

  public ImageService(S3Service s3Service, MemberService memberService,
      LectureRepository lectureRepository, LectureService lectureService) {
    this.s3Service = s3Service;
    this.memberService = memberService;
    this.lectureRepository = lectureRepository;
    this.lectureService = lectureService;
  }

  private String decodeUrl(String encodedUrl) {
    return URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);
  }

  public String uploadImage(String email, String type, MultipartFile file) throws IOException {
    validateFile(file);

    // 파일 이름 설정 (이메일 주소 기반)
    String fileName = email + type + getFileExtension(file.getOriginalFilename());
    String folderPath = "uploads/" + type + "/";
    String filePath = folderPath + fileName;
    // 현재 프로필 이미지 URL을 멤버에서 가져오기
    if (type.equals("profile")) {
      Member member = memberService.getMemberByEmail(email);
      if (member == null) {
        return "Member not found";
      }
      String existingImageUrl = member.getImage();
      if (existingImageUrl != null && !existingImageUrl.equals("0")) {
        String existingFileKey = existingImageUrl.substring(existingImageUrl.indexOf("uploads/"));
        try {
          // 기존 이미지 삭제
          s3Service.deleteFile(existingFileKey);
        } catch (Exception e) {
          return "Failed to delete existing file";
        }
      }
      try {
        String imageUrl = s3Service.uploadFile(filePath, file.getInputStream(), file.getSize());
        String decodedImageUrl = decodeUrl(imageUrl);

        member.setImage(decodedImageUrl);
        memberService.modifyMemberInfo(member);

        return "File uploaded successfully";
      } catch (IOException e) {
        return "File upload failed: " + e.getMessage();
      }
    } else if (type.equals("lecture")) {
      // 강의 이미지 업로드 처리
      Optional<Lecture> lecture = lectureService.getLectureById(Long.valueOf(email)); // 강의 ID로 변경
      if (!lecture.isPresent()) {
        return "Lecture not found";
      }
      List<LectureImage> lectureImages = lecture.get().getImages();

      try {
        // 새 이미지 업로드
        String imageUrl = s3Service.uploadFile(filePath, file.getInputStream(), file.getSize());
        String decodedImageUrl = decodeUrl(imageUrl);

        // 기존 이미지가 있다면 삭제하고, 새 이미지로 업데이트
        boolean imageUpdated = false;
        for (LectureImage lectureImage : lectureImages) {
          if (lectureImage != null && !lectureImage.getImage().equals("0")) {
            String existingFileKey = lectureImage.getImage().substring(
                lectureImage.getImage().indexOf("uploads/"));

            // 기존 이미지 삭제
            s3Service.deleteFile(existingFileKey);

            // 이미지 업데이트
            lectureImage.setImage(decodedImageUrl);
            imageUpdated = true;
            break;
          }
        }
        // 기존 이미지가 없었다면 새 이미지 추가
        if (!imageUpdated) {
          LectureImage newImage = new LectureImage();
          newImage.setImage(decodedImageUrl);
          lectureImages.add(newImage);
        }

        // 강의 정보 업데이트
        lecture.get().setImages(lectureImages);
        lectureService.updateLecture(Long.valueOf(email), lecture);

        return "Lecture image uploaded successfully";
      } catch (IOException e) {
        return "File upload failed: " + e.getMessage();
      }
    }
    return "Invalid upload type";
  }

  public String uploadLectureImage(Lecture lecture, MultipartFile file) {
    try {
      validateFile(file);

      // 파일 이름 설정 (강의 ID 기반)
      String fileName = "lecture-" + lecture.getId() + getFileExtension(file.getOriginalFilename());
      String folderPath = "uploads/lectures/";
      String filePath = folderPath + fileName;

      // 기존 이미지 URL 확인
      List<LectureImage> images = lecture.getImages();
      String existingImageUrl = images.stream()
          .filter(LectureImage::getMainImage)
          .map(LectureImage::getImage)
          .findFirst()
          .orElse(null);

      if (existingImageUrl != null && !existingImageUrl.equals("0")) {
        String existingFileKey = existingImageUrl.substring(existingImageUrl.indexOf("uploads/"));
        try {
          // 기존 이미지 삭제
          s3Service.deleteFile(existingFileKey);
        } catch (Exception e) {
          return "Failed to delete existing file";
        }
      }

      // 새 이미지 업로드
      String imageUrl = s3Service.uploadFile(filePath, file.getInputStream(), file.getSize());
      String decodedImageUrl = decodeUrl(imageUrl);

      // 새로운 이미지 엔티티 생성 및 강의에 추가
      LectureImage newImage = new LectureImage(lecture, decodedImageUrl, true);
      lecture.getImages().add(newImage);

      // 강의 업데이트
      lectureRepository.save(lecture);

      return "File uploaded successfully";
    } catch (IOException e) {
      return "File upload failed: " + e.getMessage();
    }
  }

  public String deleteImage(String email) {
    try {
      // 현재 프로필 이미지 URL을 멤버에서 가져오기
      Member member = memberService.getMemberByEmail(email);
      if (member == null) {
        return "Member not found";
      }

      // 기존 이미지 URL 확인
      String existingImageUrl = member.getImage();
      if (existingImageUrl != null && !existingImageUrl.equals("0")) {
        String existingFileKey = existingImageUrl.substring(existingImageUrl.indexOf("uploads/"));
        try {
          System.out.println(existingFileKey);
          // 기존 이미지 삭제
          s3Service.deleteFile(existingFileKey);
          member.setImage("0"); // 이미지 URL을 초기화
          memberService.modifyMemberInfo(member);
          return "Image deleted successfully";
        } catch (Exception e) {
          return "Failed to delete existing file";
        }
      } else {
        return "No image to delete";
      }
    } catch (Exception e) {
      return "Error occurred while deleting image: " + e.getMessage();
    }
  }

  private void validateFile(MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      throw new IOException("No file selected");
    }

    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new IOException("Invalid file type");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IOException("File size exceeds limit");
    }
  }

  private String getFileExtension(String originalFilename) {
    // 원본 파일명에서 확장자를 추출, 기본값은 ".png"
    int lastDotIndex = originalFilename.lastIndexOf('.');
    return (lastDotIndex >= 0) ? originalFilename.substring(lastDotIndex) : ".png";
  }
}
