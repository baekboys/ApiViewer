package com.baek.viewer.service;

import com.baek.viewer.model.GlobalConfig;
import com.baek.viewer.model.RepoConfig;
import com.baek.viewer.repository.GlobalConfigRepository;
import com.baek.viewer.repository.RepoConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * YamlConfigService 단위테스트.
 * @TempDir 로 YAML 파일을 생성해 파싱 로직 검증.
 * Repository 는 mock 으로 save 호출 횟수만 검증.
 */
@ExtendWith(MockitoExtension.class)
class YamlConfigServiceTest {

    @Mock
    private RepoConfigRepository repoRepo;

    @Mock
    private GlobalConfigRepository globalRepo;

    @InjectMocks
    private YamlConfigService service;

    @Test
    @DisplayName("importFromYamlContent — 최소 YAML 1개 레포 임포트 성공")
    void importFromYamlContent_singleRepo() throws Exception {
        String yaml = """
                global:
                  reviewThreshold: 5
                  password: testpw
                repositories:
                  - repoName: repo1
                    domain: http://example.com
                    rootPath: /tmp/repo1
                """;
        when(globalRepo.findById(1L)).thenReturn(Optional.empty());
        when(repoRepo.findByRepoName("repo1")).thenReturn(Optional.empty());
        when(repoRepo.save(any(RepoConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.importFromYamlContent(yaml);

        assertThat(result).containsEntry("added", 1);
        assertThat(result).containsEntry("updated", 0);
        verify(globalRepo, times(1)).save(any(GlobalConfig.class));
        verify(repoRepo, times(1)).save(any(RepoConfig.class));
    }

    @Test
    @DisplayName("importFromYamlContent — 기존 레포가 있으면 updated 로 카운트")
    void importFromYamlContent_existingRepo_updated() throws Exception {
        String yaml = """
                repositories:
                  - repoName: repo1
                    domain: http://x
                    rootPath: /tmp/r
                """;
        RepoConfig existing = new RepoConfig();
        existing.setId(10L);
        existing.setRepoName("repo1");
        when(repoRepo.findByRepoName("repo1")).thenReturn(Optional.of(existing));
        when(repoRepo.save(any(RepoConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.importFromYamlContent(yaml);

        assertThat(result).containsEntry("added", 0);
        assertThat(result).containsEntry("updated", 1);
    }

    @Test
    @DisplayName("importFromYamlContent — 빈 YAML 내용이면 예외")
    void importFromYamlContent_empty_throws() {
        assertThatThrownBy(() -> service.importFromYamlContent(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("importFromYaml — @TempDir 로 작성한 실제 YAML 파일 파싱")
    void importFromYaml_fromFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("repos.yml");
        Files.writeString(file, """
                global:
                  pageSize: 100
                repositories:
                  - repoName: repoFileTest
                    domain: http://f
                    rootPath: /tmp/f
                """);
        when(repoRepo.findByRepoName("repoFileTest")).thenReturn(Optional.empty());
        when(repoRepo.save(any(RepoConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.importFromYaml(file.toString());

        assertThat(result).containsEntry("added", 1);
    }

    @Test
    @DisplayName("importFromYamlContent — repoName 이 비어있으면 해당 항목 스킵")
    void importFromYamlContent_blankRepoNameSkipped() throws Exception {
        String yaml = """
                repositories:
                  - repoName: ''
                    domain: http://x
                  - repoName: valid
                    domain: http://v
                    rootPath: /tmp
                """;
        when(repoRepo.findByRepoName("valid")).thenReturn(Optional.empty());
        when(repoRepo.save(any(RepoConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.importFromYamlContent(yaml);

        assertThat(result).containsEntry("added", 1);
        verify(repoRepo, times(1)).save(any(RepoConfig.class));
    }

    @Test
    @DisplayName("getDefaultConfigPath — 기본 경로 반환")
    void getDefaultConfigPath_returnsDefault() {
        // @Value 가 주입되지 않은 상태에서는 null 이므로 ReflectionTestUtils 로 세팅
        org.springframework.test.util.ReflectionTestUtils.setField(service, "defaultConfigPath", "./repos-config.yml");
        assertThat(service.getDefaultConfigPath()).isEqualTo("./repos-config.yml");
    }
}
