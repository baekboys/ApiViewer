package com.baek.viewer.service;

import com.baek.viewer.model.ApiInfo;
import com.baek.viewer.model.ApiRecord;
import com.baek.viewer.repository.ApiRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiStorageService {

    private final ApiRecordRepository repository;

    public ApiStorageService(ApiRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * 추출된 API 목록을 저장합니다.
     * 동일 날짜 + 레포지토리가 이미 있으면 삭제 후 재삽입 (최신 상태로 갱신)
     */
    @Transactional
    public int save(String repositoryName, List<ApiInfo> apis) {
        LocalDate today = LocalDate.now();
        repository.deleteByExtractDateAndRepositoryName(today, repositoryName);

        List<ApiRecord> records = apis.stream()
                .map(a -> toRecord(today, repositoryName, a))
                .collect(Collectors.toList());

        repository.saveAll(records);
        return records.size();
    }

    private ApiRecord toRecord(LocalDate date, String repoName, ApiInfo a) {
        ApiRecord r = new ApiRecord();
        r.setExtractDate(date);
        r.setRepositoryName(repoName);
        r.setApiPath(a.getApiPath());
        r.setHttpMethod(a.getHttpMethod());
        r.setMethodName(a.getMethodName());
        r.setControllerName(a.getControllerName());
        r.setRepoPath(a.getRepoPath());
        r.setIsDeprecated(a.getIsDeprecated());
        r.setProgramId(a.getProgramId());
        r.setApiOperationValue(a.getApiOperationValue());
        r.setDescriptionTag(a.getDescriptionTag());
        r.setFullComment(a.getFullComment());
        r.setControllerComment(a.getControllerComment());
        r.setRequestPropertyValue(a.getRequestPropertyValue());
        r.setControllerRequestPropertyValue(a.getControllerRequestPropertyValue());
        r.setFullUrl(a.getFullUrl());
        r.setGitHistory(serializeGitHistory(a));
        return r;
    }

    private String serializeGitHistory(ApiInfo a) {
        List<String[]> gits = Arrays.asList(
                a.getGit1(), a.getGit2(), a.getGit3(), a.getGit4(), a.getGit5());
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String[] g : gits) {
            if (g == null || "-".equals(g[0])) continue;
            if (!first) sb.append(",");
            sb.append("{\"date\":\"").append(escJson(g[0]))
              .append("\",\"author\":\"").append(escJson(g[1]))
              .append("\",\"message\":\"").append(escJson(g[2]))
              .append("\"}");
            first = false;
        }
        return sb.append("]").toString();
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", "");
    }
}
