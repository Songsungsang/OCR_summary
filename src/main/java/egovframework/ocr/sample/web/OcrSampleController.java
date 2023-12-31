package egovframework.ocr.sample.web;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

/**
* jsp파일들의 호출을 처리하는 컨트롤러 클래스
* @author 캡스톤팀 정은총, 송창우
* @since 2023.08
* @version 0.15
* @see ocrSampleList.jsp
* @see ocrSummary.jsp
* @see OcrTestApplication.java
* 
* == 개정이력(Modification Information) ==
* 수정 내용
* 2023.08 정은총 최초 생성
* 2023.08 송창우 언어 입력, 파일 위치 처리 추가
*/
@Controller
@MultipartConfig(
        fileSizeThreshold = 1024*1024,
        maxFileSize = 1024*1024*5,
        maxRequestSize = 1024*1024*5*5
        )
public class OcrSampleController {
    /** 이미지 업로드 디렉토리 */
    //public static final String UPLOAD_DIR = "C:/Users/JEC/eclipse-workspace/ocr/src/main/java/saveImage/";
	public static final String UPLOAD_DIR = "C:/Users/Admin/git/OCR_summary/src/main/java/saveImage/";
	
    @RequestMapping(value = "/test.do", method = RequestMethod.GET)
    public String test(){
        return "ocr/ocrSampleList";
    }
    /**
    * test.do이름의 POST 타입 호출을 받아 텍스트 추출
    * @param file 이미지/pdf 폴더
    * @param lang 오타수정에 사용할 언어
    * @param model 페이지모델
    * @return ocrSampleList 화면
    * @see ocrTestApplication.java
    * @see useGPT
    */
    @RequestMapping(value = "/test.do", method = RequestMethod.POST)
    public String test(@RequestParam MultipartFile file, String language, Model model) throws IOException, ServletException {
        
        
        String fullPath = null; // path to upload image file
        if(!file.isEmpty()) {
            fullPath = UPLOAD_DIR + file.getOriginalFilename(); // set path if file is not empty
            System.out.println("File Save fullPath = " + fullPath);
            file.transferTo(new File(fullPath));
        } else {
            System.out.println("isEmpty!");
        }
        
        String fileName = ""; // 파일의 이름
        String prompt = ""; // ChatGPT에게 보낼 명령어
        String result = ""; // 테서렉트를 돌리고 안의 스페이스와 "을 없앤버전
        String preprocessingResult = ""; // ChatGPT에게 오타수정을 요청한 후 텍스트
        
        result = OcrTestApplication.OcrTest(file.getOriginalFilename(), language);
        
        prompt = "FIX_TYPO_" + language.toUpperCase(); // FIX_TYPO_KOR, FIX_TYPO_ENG
        preprocessingResult = useGPT(Prompts.getPrompt(prompt), result); // text after using ChatGPT to fix typos
        fileName = file.getOriginalFilename().replaceAll(" ", "_"); //replace all spaces with _ to prevent file name being lost
        /*Saves results to webpage model*/
        model.addAttribute("scan", result);
        model.addAttribute("result", preprocessingResult);
        model.addAttribute("fileName", fileName);
        model.addAttribute("lang", language);

        
        if (fullPath != null) { // remove temporary file
            File doDelete = new File(fullPath);
            if(doDelete.exists()) {
                doDelete.delete();
            }
        }
        
        return "ocr/ocrSampleList";
    }
    /**
     * summary.do이름의 POST 타입 호출을 받아 텍스트 요약
     * @param scanResult 추출하여 오타수정을 거친 텍스트
     * @param lang 텍스트 요약에 사용할 언어
     * @param fileName 이미지파일의 이름
     * @param model 페이지모델
     * @return ocrSummary 화면
     * @see Prompts.java
     * @see useGPT
     */
    @RequestMapping(value = "/summary.do", method = RequestMethod.POST)
    public String summary(@RequestParam String scanResult, String fileName, String lang, Model model) {
        String prompt = "SUMMARY_" + lang.toUpperCase(); // SUMMARY_KOR, SUMMARY_ENG등 언어에 맞는 요약 요청 프롬포트
        String fileTrim = fileName; // .png등 파일 포멧을 떼고 저장하기 위함
        String summaryText = ""; // 요약 텍스트를 보관
        
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            fileTrim = fileName.substring(0, dotIndex); //sample.png, sample.jpg와 같이 .을 기준으로 점 이전의 텍스트만 보관
        } else {
            System.out.println("파일에 . 이 존재하지 않습니다");
        }
        
        summaryText = useGPT(Prompts.getPrompt(prompt), scanResult);
        summaryText = summaryText.replaceAll("\\.", ".\n"); // .뒤에 엔터키를 적용"
        summaryText = summaryText.replaceAll("(?m)^[\\s&&[^\\n]]+|^[\n]", ""); // 엔터키로 인해 생긴 스페이스를 지워줌
        
        /*결과들을 웹페이지 모델에 요소들로 추가해줌*/
        model.addAttribute("fileTrim", fileTrim);
        model.addAttribute("summary", summaryText);

        return "ocr/ocrSummary";
    }

    /**
     * data.do이름의 POST 타입 호출을 받아 텍스트를 지정경로에 텍스트 파일로 저장
     * @param summary ChatGPT를 통해요약된 텍스트
     * @param fileTrim 원본파일 이름
     * @param location 파일이 저장될 
     * @param model 페이지모델
     * @return ocrSummary 화면
     */
    @RequestMapping(value = "/data.do", method = RequestMethod.POST)
    public String saveData(@RequestParam String summary, String fileTrim, String location, Model model) {
        String fileLoc = ""; // 파일이 최종 저장될 경로와 파일 이름을 합친 형태
        String message = ""; // 파일 저장 결과 출력 메세지
        
        System.out.println(fileLoc);
        if (location.endsWith("/")) {
            fileLoc = location + fileTrim + ".txt";  // 파일위치 location이 / 로 끝날경우 .txt만 추가함
        } else {
            fileLoc = location + "/" + fileTrim + ".txt";  // 파일위치 location이 /로 끝나지 않을 경우 /를 추가함
        }

        try { //fileLoc에 표시된 주소/파일이름.txt에 summary의 요약내용을 넣어 새로운 .txt파일 생성 
            FileWriter writer = new FileWriter(fileLoc); // 파일처리를 위한 변수

            writer.write(summary);

            writer.close();

            message = "파일생성 완료!"; // 파일 저장 성공시 메세지
        } catch (IOException e) {
            message = "파일생성 실패: " + e.getMessage();
        }

        /*결과들을 웹페이지 모델에 요소들로 추가해줌*/
        model.addAttribute("summary", summary);
        model.addAttribute("fileTrim", fileTrim);
        model.addAttribute("message", message);
        model.addAttribute("location", location);

        return "ocr/ocrSummary";
    }
    
    /**
     * data.do이름의 POST 타입 호출을 받아 텍스트를 지정경로에 텍스트 파일로 저장
     * @param prompt ChatGPT에게 명령을 주기위한 명령 텍스트
     * @param content ChatGPT에게 보낼 텍스트
     * @see Prompts.java
     * @see Keys.java
     * @return ChatGPT를 통해 생성된 텍스트
     */
    public String useGPT(String prompt, String content) {
        Keys keysInstance = Keys.getInstance(); // OpenAI API 활용을 위한 키 인스턴스
        String gptKey = keysInstance.getGptKey(); // 인스턴스의 키값
        OpenAiService service = new OpenAiService(gptKey,Duration.ofMinutes(9999)); // OpenAI 서비스 연결

        List<ChatMessage> message = new ArrayList<ChatMessage>(); // GPT에게 보낼 메세지 어레이
        message.add(new ChatMessage("user", prompt));
        message.add(new ChatMessage("user", content));
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder() // OpenAI의 모델 선택하여 메세지 전달후 결과 텍스트 받기
                .messages(message)
                .model("gpt-3.5-turbo") // 터보 모델 사용
                .maxTokens(1000)
                .temperature((double) 1.0f) // 답변의 자유도 설정
                .build();

        return service.createChatCompletion(completionRequest).getChoices().get(0)
                .getMessage().getContent();
    }
}