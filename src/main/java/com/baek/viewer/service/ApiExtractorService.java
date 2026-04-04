package com.baek.viewer.service;

import com.baek.viewer.model.ApiInfo;
import com.baek.viewer.model.ExtractRequest;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class ApiExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ApiExtractorService.class);

    private static final List<String> MAPPING_ANNS = Arrays.asList(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");

    @Value("${api.viewer.git-bin-path:git}")
    private String defaultGitBinPath;

    private final ApiStorageService storageService;

    public ApiExtractorService(ApiStorageService storageService) {
        this.storageService = storageService;
    }

    private volatile List<ApiInfo> cachedApis = new ArrayList<>();
    private volatile boolean extracting = false;
    private volatile int totalFiles = 0;
    private volatile int processedFiles = 0;
    private volatile String currentFile = "";
    private volatile String lastError = null;
    private volatile int savedCount = -1; // -1 = 미저장, 0 이상 = 저장 건수
    private final List<String> extractLogs = Collections.synchronizedList(new ArrayList<>());

    private void addLog(String level, String msg) {
        String ts = java.time.LocalTime.now().toString().substring(0, 8);
        extractLogs.add(ts + " [" + level + "] " + msg);
    }

    public boolean isExtracting() { return extracting; }
    public List<ApiInfo> getCached() { return cachedApis; }

    public Map<String, Object> getProgress() {
        Map<String, Object> p = new HashMap<>();
        p.put("extracting", extracting);
        p.put("total", totalFiles);
        p.put("processed", processedFiles);
        p.put("currentFile", currentFile);
        p.put("percent", totalFiles > 0 ? (processedFiles * 100 / totalFiles) : 0);
        p.put("error", lastError);
        p.put("savedCount", savedCount);
        p.put("logs", new ArrayList<>(extractLogs));
        return p;
    }

    public void startExtractAsync(ExtractRequest req) {
        savedCount = -1;
        extractLogs.clear();
        new Thread(() -> extract(req)).start();
    }

    // ======================================================
    // 메인 추출 진입점
    // ======================================================

    public List<ApiInfo> extract(ExtractRequest req) {
        if (extracting) throw new IllegalStateException("이미 추출 중입니다.");
        extracting = true;
        log.info("[추출 시작] rootPath={}, repo={}", req.getRootPath(), req.getRepositoryName());

        String rootPath = req.getRootPath();
        String domain = req.getDomain() != null ? req.getDomain() : "";
        String apiPathPrefix = req.getApiPathPrefix() != null ? req.getApiPathPrefix() : "";
        String gitBin = (req.getGitBinPath() != null && !req.getGitBinPath().isBlank())
                ? req.getGitBinPath() : defaultGitBinPath;
        Map<String, String> pathConstantsMap = parsePathConstants(req.getPathConstants());

        List<ApiInfo> apis = new CopyOnWriteArrayList<>();
        lastError = null;

        addLog("INFO", "추출 시작 — 경로: " + rootPath);
        if (req.getRepositoryName() != null && !req.getRepositoryName().isBlank()) {
            addLog("INFO", "레포지토리: " + req.getRepositoryName());
        }

        try {
            Path root = Paths.get(rootPath);
            if (!Files.exists(root)) throw new IllegalArgumentException("경로가 존재하지 않습니다: " + rootPath);

            List<Path> controllerFiles = Files.walk(root)
                    .filter(p -> p.toString().endsWith(".java") &&
                            (p.toString().contains("Controller") || p.toString().contains("Conrtoller")))
                    .collect(Collectors.toList());

            totalFiles = controllerFiles.size();
            processedFiles = 0;
            addLog("INFO", "Controller 파일 " + totalFiles + "개 발견");

            controllerFiles.parallelStream().forEach(file -> {
                String rel = root.relativize(file).toString();
                String fileName = file.getFileName().toString();
                currentFile = fileName;
                try {
                    List<String[]> git = getRecentGitHistories(rel, rootPath, gitBin, 5);
                    List<ApiInfo> fileApis = extractApisHybrid(file, rel, git, apiPathPrefix, pathConstantsMap);
                    apis.addAll(fileApis);
                    addLog("OK", fileName + " — " + fileApis.size() + "개 API 추출");
                } catch (Exception e) {
                    addLog("ERROR", fileName + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                processedFiles++;
            });

        } catch (Exception e) {
            lastError = e.getMessage();
            addLog("ERROR", "추출 실패: " + e.getMessage());
            log.error("[추출 실패] rootPath={}, 오류={}", rootPath, e.getMessage(), e);
            extracting = false;
            throw new RuntimeException("추출 실패: " + e.getMessage(), e);
        }

        // API 경로 정렬 후 도메인 + full URL 보정
        List<ApiInfo> sorted = apis.stream()
                .sorted(Comparator.comparing(ApiInfo::getApiPath))
                .collect(Collectors.toList());

        for (ApiInfo info : sorted) {
            info.setFullUrl(domain + info.getApiPath());
        }

        cachedApis = sorted;
        addLog("INFO", "추출 완료 — 총 " + sorted.size() + "개 API");
        log.info("[추출 완료] 총 {}개 API, 파일 {}개 처리", sorted.size(), totalFiles);

        // DB 저장 (레포지토리명이 있을 때만)
        String repoName = req.getRepositoryName();
        if (repoName != null && !repoName.isBlank()) {
            try {
                addLog("INFO", "DB 저장 중 — 레포: " + repoName.trim());
                savedCount = storageService.save(repoName.trim(), cachedApis, req.getClientIp());
                addLog("OK", "DB 저장 완료 — " + savedCount + "개 저장/갱신");
            } catch (Exception e) {
                savedCount = -1;
                addLog("ERROR", "DB 저장 실패: " + e.getMessage());
            }
        }

        extracting = false;
        return cachedApis;
    }

    // ======================================================
    // 하이브리드 추출 (JavaParser 우선, Regex 폴백)
    // ======================================================

    private List<ApiInfo> extractApisHybrid(Path path, String rel,
                                             List<String[]> git,
                                             String apiPathPrefix,
                                             Map<String, String> pathConstantsMap) {
        try {
            return extractWithJavaParser(path, rel, git, apiPathPrefix, pathConstantsMap);
        } catch (Exception e) {
            addLog("WARN", path.getFileName() + " — JavaParser 실패 (" + e.getClass().getSimpleName() + "), Regex 폴백 적용");
            log.warn("[Regex 폴백] 파일={}, 사유={}", path.getFileName(), e.getMessage());
            return extractWithRegex(path, rel, git, apiPathPrefix, pathConstantsMap);
        }
    }

    // ======================================================
    // JavaParser 기반 추출
    // ======================================================

    private List<ApiInfo> extractWithJavaParser(Path filePath, String relPath,
                                                 List<String[]> git,
                                                 String apiPathPrefix,
                                                 Map<String, String> pathConstantsMap) throws Exception {
        List<ApiInfo> apis = new ArrayList<>();
        String source = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);

        String classPath = "";
        String controllerComment = "-";
        String controllerRequestProperty = "-";

        Optional<ClassOrInterfaceDeclaration> mainClass = cu.findFirst(ClassOrInterfaceDeclaration.class);
        if (mainClass.isPresent()) {
            ClassOrInterfaceDeclaration cls = mainClass.get();
            controllerComment = cls.getComment()
                    .map(c -> c.getContent().replaceAll("[\\r\\n*]", " ").trim())
                    .orElse("-");
            controllerRequestProperty = extractRequestPropertyFromNode(cls);

            Optional<AnnotationExpr> classAnn = cls.getAnnotationByName("RequestMapping");
            if (classAnn.isPresent()) {
                List<String> paths = getPathsFromAnn(classAnn.get(), pathConstantsMap);
                if (!paths.isEmpty()) classPath = paths.get(0).trim();
            }
        }

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            for (String annName : MAPPING_ANNS) {
                Optional<AnnotationExpr> methodAnn = method.getAnnotationByName(annName);
                if (methodAnn.isEmpty()) continue;

                String httpMethod = resolveHttpMethod(annName, methodAnn.get());
                List<String> subPaths = getPathsFromAnn(methodAnn.get(), pathConstantsMap);
                if (subPaths.isEmpty()) subPaths.add("");

                for (String sub : subPaths) {
                    String finalPath = normalizePath(apiPathPrefix + classPath + "/" + sub.trim());
                    ApiInfo info = buildApiInfo(filePath, relPath, method, git,
                            finalPath, httpMethod, controllerComment, controllerRequestProperty);
                    apis.add(info);
                }
            }
        }
        return apis;
    }

    // ======================================================
    // Regex 기반 폴백 추출
    // ======================================================

    private List<ApiInfo> extractWithRegex(Path filePath, String relPath,
                                            List<String[]> git,
                                            String apiPathPrefix,
                                            Map<String, String> pathConstantsMap) {
        List<ApiInfo> apis = new ArrayList<>();
        try {
            String raw = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            String clean = raw.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//.*", " ");

            String controllerComment = "-";
            Matcher cM = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL).matcher(raw);
            if (cM.find()) controllerComment = cM.group(1).replaceAll("[\\r\\n*]", " ").trim();

            String classPath = "";
            String classHead = clean.substring(0, Math.min(clean.length(), 3000));
            Matcher cm = Pattern.compile("@RequestMapping\\s*\\((.*?)\\)", Pattern.DOTALL).matcher(classHead);
            if (cm.find()) {
                String cParams = substituteConstants(cm.group(1), pathConstantsMap)
                        .replaceAll("\"\\s*\\+\\s*\"", "");
                Matcher cp = Pattern.compile("\"([^\"]+)\"").matcher(cParams);
                if (cp.find()) classPath = cp.group(1).trim();
            }

            Matcher mMatcher = Pattern.compile(
                    "@(GetMapping|PostMapping|RequestMapping|PutMapping|DeleteMapping|PatchMapping)\\s*\\((.*?)\\)",
                    Pattern.DOTALL).matcher(raw);

            while (mMatcher.find()) {
                String mappingType = mMatcher.group(1);
                String params = substituteConstants(mMatcher.group(2), pathConstantsMap)
                        .replaceAll("\"\\s*\\+\\s*\"", "");
                String httpMethod = resolveHttpMethodFromName(mappingType, params);

                String afterMapping = clean.substring(mMatcher.end(), Math.min(mMatcher.end() + 1000, clean.length()));
                Matcher mName = Pattern.compile("(?:public|private|protected)\\s+[\\w<>,\\s]+\\s+(\\w+)\\s*\\(")
                        .matcher(afterMapping);
                if (!mName.find()) continue;

                String methodName = mName.group(1);
                boolean isDeprecated = clean.substring(Math.max(0, mMatcher.start() - 300), mMatcher.start())
                        .contains("@Deprecated");

                Matcher p = Pattern.compile("\"([^\"]+)\"").matcher(params);
                boolean found = false;
                while (p.find()) {
                    String s = p.group(1).trim();
                    if (s.contains("RequestMethod")) continue;
                    found = true;

                    String finalPath = normalizePath(apiPathPrefix + classPath + "/" + s);
                    String headArea = raw.substring(Math.max(0, mMatcher.start() - 1000), mMatcher.start());

                    ApiInfo info = new ApiInfo();
                    info.setApiPath(finalPath);
                    info.setHttpMethod(httpMethod);
                    info.setMethodName(methodName);
                    info.setControllerName(filePath.getFileName().toString());
                    info.setRepoPath(relPath.replace("\\", "/"));
                    info.setIsDeprecated(isDeprecated ? "Y" : "N");
                    String mBody = afterMapping.substring(mName.end(), Math.min(mName.end() + 500, afterMapping.length()));
                    info.setHasUrlBlock(detectUrlBlockRegex(mBody) ? "Y" : "N");
                    info.setProgramId(autoExtractProgramId(finalPath));
                    info.setControllerComment(controllerComment);
                    info.setGit1(git.get(0)); info.setGit2(git.get(1)); info.setGit3(git.get(2));
                    info.setGit4(git.get(3)); info.setGit5(git.get(4));

                    Matcher docM = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL).matcher(headArea);
                    if (docM.find()) {
                        String doc = docM.group(1);
                        info.setFullComment(doc.replaceAll("[\\r\\n*]", " ").trim());
                        Matcher dM = Pattern.compile("@?(description|deprecation)[\\s:]*([^@\\n\\r*]+)",
                                Pattern.CASE_INSENSITIVE).matcher(doc);
                        info.setDescriptionTag(dM.find() ? dM.group(2).trim() : "-");
                    } else {
                        info.setFullComment("-"); info.setDescriptionTag("-");
                    }
                    // @Deprecated 라인에서 [URL차단작업] 정보 보충
                    if (isDeprecated && (info.getFullComment().equals("-") || !info.getFullComment().contains("[URL차단작업]"))) {
                        String depLine = extractDeprecatedLine(headArea);
                        if (depLine != null && depLine.contains("[URL차단작업]")) {
                            info.setFullComment(depLine);
                        }
                    }

                    // @ApiOperation 우선, 없으면 @Operation(summary) 폴백
                    Matcher aM = Pattern.compile("@ApiOperation\\s*\\(.*?value\\s*=\\s*\"([^\"]+)\".*?\\)",
                            Pattern.DOTALL).matcher(headArea);
                    if (aM.find()) {
                        info.setApiOperationValue(aM.group(1));
                    } else {
                        Matcher opM = Pattern.compile("@Operation\\s*\\(.*?summary\\s*=\\s*\"([^\"]+)\".*?\\)",
                                Pattern.DOTALL).matcher(headArea);
                        info.setApiOperationValue(opM.find() ? opM.group(1) : "-");
                    }
                    info.setRequestPropertyValue("-");
                    info.setControllerRequestPropertyValue("-");
                    apis.add(info);
                }

                if (!found) {
                    // 매핑 어노테이션은 있으나 경로 문자열이 없는 경우 (빈 매핑)
                    String finalPath = normalizePath(apiPathPrefix + classPath);
                    ApiInfo info = new ApiInfo();
                    info.setApiPath(finalPath.isEmpty() ? "/" : finalPath);
                    info.setHttpMethod(httpMethod);
                    info.setMethodName(methodName);
                    info.setControllerName(filePath.getFileName().toString());
                    info.setRepoPath(relPath.replace("\\", "/"));
                    info.setIsDeprecated(isDeprecated ? "Y" : "N");
                    String mBody2 = afterMapping.substring(mName.end(), Math.min(mName.end() + 500, afterMapping.length()));
                    info.setHasUrlBlock(detectUrlBlockRegex(mBody2) ? "Y" : "N");
                    info.setProgramId(autoExtractProgramId(finalPath));
                    info.setControllerComment(controllerComment);
                    info.setGit1(git.get(0)); info.setGit2(git.get(1)); info.setGit3(git.get(2));
                    info.setGit4(git.get(3)); info.setGit5(git.get(4));
                    info.setFullComment("-"); info.setDescriptionTag("-");
                    info.setApiOperationValue("-");
                    info.setRequestPropertyValue("-");
                    info.setControllerRequestPropertyValue("-");
                    apis.add(info);
                }
            }
        } catch (Exception ignored) {}
        return apis;
    }

    // ======================================================
    // ApiInfo 빌더 (JavaParser용)
    // ======================================================

    private ApiInfo buildApiInfo(Path filePath, String relPath, MethodDeclaration method,
                                  List<String[]> git, String finalPath, String httpMethod,
                                  String controllerComment, String controllerRequestProperty) {
        ApiInfo info = new ApiInfo();
        info.setApiPath(finalPath.isEmpty() ? "/" : finalPath);
        info.setHttpMethod(httpMethod);
        info.setMethodName(method.getNameAsString());
        info.setControllerName(filePath.getFileName().toString());
        info.setRepoPath(relPath.replace("\\", "/"));
        info.setIsDeprecated(method.isAnnotationPresent("Deprecated") ? "Y" : "N");
        info.setHasUrlBlock(detectUrlBlock(method) ? "Y" : "N");
        info.setProgramId(autoExtractProgramId(finalPath));
        info.setControllerComment(controllerComment);
        info.setControllerRequestPropertyValue(controllerRequestProperty);
        info.setGit1(git.get(0)); info.setGit2(git.get(1)); info.setGit3(git.get(2));
        info.setGit4(git.get(3)); info.setGit5(git.get(4));

        if (method.getComment().isPresent()) {
            String doc = method.getComment().get().getContent();
            info.setFullComment(doc.replaceAll("[\\r\\n*]", " ").trim());
            Matcher dM = Pattern.compile("@?(description|deprecation)[\\s:]*([^@\\n\\r*]+)",
                    Pattern.CASE_INSENSITIVE).matcher(doc);
            info.setDescriptionTag(dM.find() ? dM.group(2).trim() : "-");
        } else {
            info.setFullComment("-"); info.setDescriptionTag("-");
        }
        // @Deprecated 라인에서 [URL차단작업] 정보 보충
        if ("Y".equals(info.getIsDeprecated()) && (info.getFullComment().equals("-") || !info.getFullComment().contains("[URL차단작업]"))) {
            try {
                String src = Files.readString(filePath, StandardCharsets.UTF_8);
                String depLine = extractDeprecatedLine(src);
                if (depLine != null && depLine.contains("[URL차단작업]")) {
                    info.setFullComment(depLine);
                }
            } catch (Exception ignore) {}
        }

        info.setRequestPropertyValue(extractRequestPropertyFromNode(method));

        // @ApiOperation 우선, 없으면 @Operation(summary) 폴백
        String op = extractAnnotationValue(method, "ApiOperation", "value");
        if ("-".equals(op)) op = extractAnnotationValue(method, "Operation", "summary");
        info.setApiOperationValue(op);

        return info;
    }

    // ======================================================
    // JavaParser 헬퍼
    // ======================================================

    private List<String> getPathsFromAnn(AnnotationExpr ann, Map<String, String> constantsMap) {
        List<String> paths = new ArrayList<>();
        Expression value = null;
        if (ann instanceof SingleMemberAnnotationExpr se) {
            value = se.getMemberValue();
        } else if (ann instanceof NormalAnnotationExpr ne) {
            value = ne.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                    .map(MemberValuePair::getValue).findFirst().orElse(null);
        }

        if (value instanceof ArrayInitializerExpr ae) {
            for (Expression expr : ae.getValues()) {
                String eval = evaluateExpression(expr, constantsMap);
                if (!eval.isEmpty()) paths.add(eval);
            }
        } else if (value != null) {
            String eval = evaluateExpression(value, constantsMap);
            if (!eval.isEmpty()) paths.add(eval);
        }
        return paths;
    }

    private String evaluateExpression(Expression expr, Map<String, String> constantsMap) {
        if (expr instanceof StringLiteralExpr sl) return sl.getValue();
        if (expr instanceof BinaryExpr be && be.getOperator() == BinaryExpr.Operator.PLUS)
            return evaluateExpression(be.getLeft(), constantsMap) + evaluateExpression(be.getRight(), constantsMap);
        if (expr instanceof FieldAccessExpr || expr instanceof NameExpr)
            return constantsMap.getOrDefault(expr.toString(), "{" + expr + "}");
        return "";
    }

    private String extractAnnotationValue(MethodDeclaration method, String annName, String attrName) {
        Optional<AnnotationExpr> ann = method.getAnnotationByName(annName);
        if (ann.isEmpty()) return "-";
        if (ann.get() instanceof NormalAnnotationExpr ne) {
            return ne.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals(attrName))
                    .map(p -> p.getValue().toString().replaceAll("\"", ""))
                    .findFirst().orElse("-");
        }
        if (ann.get() instanceof SingleMemberAnnotationExpr se && "value".equals(attrName)) {
            return se.getMemberValue().toString().replaceAll("\"", "");
        }
        return "-";
    }

    private String extractRequestPropertyFromNode(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node) {
        Optional<AnnotationExpr> ann = node.getAnnotationByName("RequestProperty");
        if (ann.isEmpty()) return "-";
        if (ann.get() instanceof NormalAnnotationExpr ne) {
            String title = ne.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("title"))
                    .map(p -> p.getValue().toString().replaceAll("\"", ""))
                    .findFirst().orElse(null);
            if (title != null) return title;
            return ne.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .map(p -> p.getValue().toString().replaceAll("\"", ""))
                    .findFirst().orElse("-");
        }
        if (ann.get() instanceof SingleMemberAnnotationExpr se)
            return se.getMemberValue().toString().replaceAll("\"", "");
        return "-";
    }

    // ======================================================
    // HTTP 메소드 판별
    // ======================================================

    private String resolveHttpMethod(String annName, AnnotationExpr ann) {
        if (!annName.equals("RequestMapping")) return annName.replace("Mapping", "").toUpperCase();
        // RequestMapping은 method 속성 확인
        if (ann instanceof NormalAnnotationExpr ne) {
            Optional<String> method = ne.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("method"))
                    .map(p -> p.getValue().toString())
                    .findFirst();
            if (method.isPresent()) {
                String m = method.get().toUpperCase();
                if (m.contains("GET")) return "GET";
                if (m.contains("POST")) return "POST";
                if (m.contains("PUT")) return "PUT";
                if (m.contains("DELETE")) return "DELETE";
                if (m.contains("PATCH")) return "PATCH";
            }
        }
        return "REQUEST";
    }

    private String resolveHttpMethodFromName(String mappingType, String params) {
        if (!mappingType.equals("RequestMapping")) return mappingType.replace("Mapping", "").toUpperCase();
        String upper = params.toUpperCase();
        if (upper.contains("GET")) return "GET";
        if (upper.contains("POST")) return "POST";
        if (upper.contains("PUT")) return "PUT";
        if (upper.contains("DELETE")) return "DELETE";
        if (upper.contains("PATCH")) return "PATCH";
        return "REQUEST";
    }

    // ======================================================
    // 프로그램 ID 자동 추출 (ApiExcelExporter 동일 로직)
    // ======================================================

    /**
     * JavaParser: 메소드 본문 첫 statement가 UnsupportedOperationException throw인지 판단.
     * if(true) throw new UnsupportedOperationException("..."); 패턴 포함.
     */
    private boolean detectUrlBlock(MethodDeclaration method) {
        if (method.getBody().isEmpty()) return false;
        var stmts = method.getBody().get().getStatements();
        if (stmts.isEmpty()) return false;
        String first = stmts.get(0).toString();
        return first.contains("UnsupportedOperationException");
    }

    /** Regex 폴백: 메소드 본문 초반에서 UnsupportedOperationException throw 패턴 검색 */
    private boolean detectUrlBlockRegex(String methodBodySnippet) {
        if (methodBodySnippet == null) return false;
        return Pattern.compile("throw\\s+new\\s+UnsupportedOperationException", Pattern.CASE_INSENSITIVE)
                .matcher(methodBodySnippet).find();
    }

    /** @Deprecated 라인에서 [URL차단작업] 이후 전체 텍스트 추출 */
    private String extractDeprecatedLine(String source) {
        if (source == null) return null;
        Matcher m = Pattern.compile("@Deprecated\\s+(.+)", Pattern.MULTILINE).matcher(source);
        if (m.find()) {
            String line = m.group(1).trim();
            if (line.contains("[URL차단작업]")) return line;
        }
        // 여러 줄에 걸친 경우: @Deprecated 다음 줄에 [URL차단작업]
        Matcher m2 = Pattern.compile("@Deprecated[\\s\\S]*?(\\[URL차단작업\\].+)", Pattern.MULTILINE).matcher(source);
        if (m2.find()) return m2.group(1).trim();
        return null;
    }

    private String autoExtractProgramId(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return "-";
        if (path.contains(".")) {
            String nameOnly = path.substring(path.lastIndexOf("/") + 1).split("\\.")[0];
            return nameOnly.contains("_") ? nameOnly.substring(0, nameOnly.lastIndexOf("_")) : nameOnly;
        }
        String[] segments = path.split("/");
        List<String> valid = new ArrayList<>();
        List<String> actions = Arrays.asList("new", "edit", "update", "delete", "create", "list", "save", "view");
        for (String s : segments)
            if (!s.isEmpty() && !s.startsWith("{") && !actions.contains(s.toLowerCase())) valid.add(s);
        return valid.isEmpty() ? "-" : valid.get(valid.size() - 1);
    }

    // ======================================================
    // Git 히스토리 조회
    // ======================================================

    private List<String[]> getRecentGitHistories(String rel, String root, String gitBin, int count) {
        List<String[]> h = new ArrayList<>();
        for (int i = 0; i < count; i++) h.add(new String[]{"-", "-", "No History"});
        try {
            Process p = new ProcessBuilder(gitBin, "log", "-" + count,
                    "--pretty=format:%as|%an|%s", "--", rel)
                    .directory(new File(root)).start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
                for (int i = 0; i < Math.min(lines.size(), count); i++) {
                    String[] parts = lines.get(i).split("\\|", 3);
                    if (parts.length >= 2)
                        h.set(i, new String[]{parts[0], parts[1], parts.length > 2 ? parts[2] : ""});
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return h;
    }

    // ======================================================
    // 유틸리티
    // ======================================================

    private String normalizePath(String path) {
        String result = path.replaceAll("/+", "/");
        if (result.isEmpty()) return "/";
        return result;
    }

    private String substituteConstants(String text, Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet())
            text = text.replace(e.getKey(), "\"" + e.getValue() + "\"");
        return text;
    }

    private Map<String, String> parsePathConstants(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }
}