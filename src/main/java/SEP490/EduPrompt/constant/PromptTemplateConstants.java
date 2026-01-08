package SEP490.EduPrompt.constant;

import SEP490.EduPrompt.enums.PromptTask;

public class PromptTemplateConstants {

    public static final String LESSON_PLAN_TEMPLATE = """
            ## Context

            Bạn là **giáo viên THPT tại Việt Nam**, giảng dạy theo **Chương trình Giáo dục Phổ thông 2018**, định hướng **phát triển phẩm chất và năng lực học sinh**.

            Bài học cần thiết kế có thông tin tổng quát như sau:

            * **Môn học:** {{TÊN_MÔN_HỌC}}
            * **Khối lớp:** {{KHỐI_LỚP}}
            * **Chương / Chủ đề:** {{CHƯƠNG_HOẶC_CHỦ_ĐỀ}}
            * **Bài học:** {{TÊN_BÀI_HỌC}}
            * **Thời lượng:** {{SỐ_TIẾT}} ({{TỔNG_THỜI_GIAN_PHÚT}} phút)

            **Mục tiêu trọng tâm của bài học:**
            {{MÔ_TẢ_MỤC_TIÊU_CHÍNH_CỦA_BÀI}}

            **Phạm vi nội dung kiến thức của bài:**
            {{LIỆT_KÊ_CÁC_NỘI_DUNG_CHÍNH_CẦN_DẠY}}

            Giáo án cần bám sát **chuẩn kiến thức – kĩ năng của chương trình hiện hành**, gắn với **các dạng bài tập/hoạt động trong sách giáo khoa**, nhưng **không chép nguyên văn sách giáo khoa**.

            ---

            ## Instruction

            Thiết kế **một giáo án hoàn chỉnh** cho bài học đã nêu trong phần Context, theo định hướng **lấy học sinh làm trung tâm**, phát triển **năng lực và phẩm chất**.

            Giáo án phải thể hiện rõ các nội dung sau:

            ---

            ### 1. Mục tiêu bài học

            Trình bày đầy đủ và rõ ràng:

            * **Kiến thức:** những nội dung cốt lõi học sinh cần nắm được sau bài học.
            * **Kĩ năng:** các kĩ năng chuyên môn của môn học và kĩ năng học tập.
            * **Thái độ, phẩm chất:** (chăm chỉ, trung thực, trách nhiệm, nhân ái,… tùy môn học).
            * **Năng lực:**

              * Năng lực đặc thù của môn học.
              * Năng lực chung (tự học, giao tiếp, hợp tác, giải quyết vấn đề).

            ---

            ### 2. Chuẩn bị

            * **Giáo viên:** kế hoạch bài dạy, thiết bị dạy học, học liệu (slide, bảng phụ, phiếu học tập,… nếu có).
            * **Học sinh:** kiến thức nền, đồ dùng học tập cần thiết.

            ---

            ### 3. Tiến trình dạy học

            Thiết kế theo các **hoạt động dạy học cơ bản**:

            * **Hoạt động 1: Khởi động**
            * **Hoạt động 2: Hình thành kiến thức**
            * **Hoạt động 3: Luyện tập**
            * **Hoạt động 4: Vận dụng – mở rộng** *(nếu còn thời gian)*
            * **Củng cố – dặn dò**

            Với **mỗi hoạt động**, cần nêu rõ:

            * Mục tiêu của hoạt động
            * Nhiệm vụ giao cho học sinh
            * Cách tổ chức (cá nhân / nhóm / cả lớp)
            * Thời lượng dự kiến
            * Sản phẩm học tập
            * Vai trò tổ chức, hỗ trợ, gợi mở của giáo viên
            * Năng lực chính được hình thành hoặc rèn luyện

            Khuyến khích:

            * Có **hoạt động nhóm** để học sinh thảo luận, trình bày, phản biện.
            * Giáo viên đóng vai trò **tổ chức – định hướng**, hạn chế thuyết giảng một chiều.

            ---

            ### 4. Hệ thống câu hỏi / bài tập / nhiệm vụ học tập

            Thiết kế các câu hỏi, bài tập hoặc nhiệm vụ:

            * Phù hợp nội dung bài học và đối tượng học sinh.
            * Có mức độ từ **nhận biết → thông hiểu → vận dụng**.
            * Gắn với kiến thức môn học hoặc tình huống thực tiễn đơn giản.

            Có thể kèm:

            * bài tập cá nhân,
            * bài tập nhóm,
            * câu hỏi thảo luận,
            * câu hỏi trắc nghiệm nhanh.

            ---

            ### 5. Kiểm tra, đánh giá trong giờ học

            * **Hình thức:** hỏi – đáp, bài tập nhanh, sản phẩm nhóm, trình bày miệng, trắc nghiệm cuối giờ,…
            * **Tiêu chí đánh giá:**

              * đúng kiến thức,
              * logic, rõ ràng,
              * phù hợp yêu cầu môn học,
              * mức độ tham gia và hợp tác của học sinh.

            ---

            ## Input example

            Lớp dạy: {{TÊN_LỚP}}, trình độ {{MỨC_ĐỘ_HỌC_SINH}}.
            Điều kiện lớp học: {{CƠ_SỞ_VẬT_CHẤT}}.

            Yêu cầu cụ thể:

            * {{YÊU_CẦU_TRỌNG_TÂM_1}}
            * {{YÊU_CẦU_TRỌNG_TÂM_2}}
            * {{YÊU_CẦU_VỀ_HOẠT_ĐỘNG_NHÓM / PHÂN_HÓA}}
            * {{YÊU_CẦU_VỀ_ĐÁNH_GIÁ_CUỐI_GIỜ}}

            ---

            ## Output format

            Xuất kết quả dưới dạng **một giáo án chi tiết**, trình bày rõ ràng theo cấu trúc:

            1. Thông tin bài học
            2. Mục tiêu bài học
            3. Chuẩn bị
            4. Tiến trình dạy học

               * Hoạt động 1: Khởi động
               * Hoạt động 2: Hình thành kiến thức
               * Hoạt động 3: Luyện tập
               * Hoạt động 4: Vận dụng – mở rộng
               * Củng cố và dặn dò
            5. Kiểm tra, đánh giá
            6. Rút kinh nghiệm (để trống cho giáo viên ghi sau giờ dạy)

            ---

            ## Constraint

            * Không chép nguyên văn sách giáo khoa; chỉ diễn đạt lại bằng lời của bạn.
            * Nội dung đúng với chương trình hiện hành của Bộ GD&ĐT Việt Nam.
            * Không đưa kiến thức vượt quá phạm vi bài học.
            * Phân bổ thời lượng hợp lí, không gây quá tải.
            * Ngôn ngữ sư phạm, trong sáng, dễ hiểu, phù hợp học sinh.
            * **Mỗi hoạt động trình bày gọn (khoảng 8–12 dòng); tổng giáo án tương đương 3–4 trang A4 khi in.**
            * Có gợi ý **phân hóa nhiệm vụ** cho học sinh yếu và mở rộng cho học sinh khá – giỏi (ở mức phù hợp).

            ---
            """;

    public static final String SLIDE_TEMPLATE = """
            ## Context

            Bạn là **giáo viên THPT tại Việt Nam**, giảng dạy theo **Chương trình Giáo dục Phổ thông 2018**, có kinh nghiệm thiết kế **slide bài giảng phục vụ dạy học trên lớp** theo định hướng **lấy học sinh làm trung tâm**.

            Bài học cần thiết kế slide có thông tin sau:

            * **Môn học:** {{TÊN_MÔN_HỌC}}
            * **Khối lớp:** {{KHỐI_LỚP}}
            * **Chương / Chủ đề:** {{CHƯƠNG_HOẶC_CHỦ_ĐỀ}}
            * **Bài học:** {{TÊN_BÀI_HỌC}}
            * **Thời lượng:** {{SỐ_TIẾT}} ({{TỔNG_THỜI_GIAN_PHÚT}} phút)

            **Mục tiêu trọng tâm của bài học:**
            {{MÔ_TẢ_MỤC_TIÊU_CHÍNH}}

            **Phạm vi nội dung kiến thức của bài:**
            {{LIỆT_KÊ_CÁC_NỘI_DUNG_CHÍNH}}

            Slide được sử dụng **trong giờ học trực tiếp**, kết hợp với hoạt động cá nhân/nhóm của học sinh (không phải slide tự học).

            ---

            ## Instruction

            Thiết kế **một bộ slide bài giảng hoàn chỉnh** cho bài học trên, phù hợp dạy học THPT theo GDPT 2018.

            Yêu cầu chung:

            * Slide **ngắn gọn – trực quan – dễ trình chiếu**
            * Nội dung trên slide chỉ đóng vai trò **gợi mở, dẫn dắt**, không viết như giáo trình
            * Mỗi slide tập trung **một ý chính**
            * Ngôn ngữ sư phạm, phù hợp học sinh

            ---

            ### 1. Cấu trúc bộ slide

            Bộ slide cần bao gồm các phần sau (theo trình tự dạy học):

            1. **Slide mở đầu**

               * Tên bài học
               * Môn – lớp
               * Mục tiêu chính (1–2 ý ngắn)

            2. **Slide khởi động**

               * Câu hỏi / tình huống / hình ảnh gợi vấn đề
               * Mục đích: kích hoạt tư duy, dẫn vào bài mới

            3. **Slide hình thành kiến thức**

               * Mỗi nội dung kiến thức chính: **1–2 slide**
               * Trình bày:

                 * khái niệm (ngắn gọn),
                 * ví dụ minh họa,
                 * câu hỏi gợi mở cho học sinh
               * Không trình bày quá nhiều chữ trên một slide

            4. **Slide luyện tập**

               * Bài tập ngắn, câu hỏi trắc nghiệm hoặc nhiệm vụ nhóm
               * Có chỗ để học sinh suy nghĩ, thảo luận

            5. **Slide vận dụng – mở rộng**

               * Tình huống thực tiễn đơn giản
               * Câu hỏi yêu cầu học sinh liên hệ hoặc giải thích

            6. **Slide củng cố**

               * Sơ đồ, bảng tổng hợp hoặc câu hỏi hệ thống hóa kiến thức

            7. **Slide dặn dò**

               * Nhiệm vụ về nhà
               * Gợi ý chuẩn bị cho bài học tiếp theo

            ---

            ### 2. Yêu cầu chi tiết cho từng slide

            Với **mỗi slide**, cần nêu rõ:

            * **Tiêu đề slide**
            * **Nội dung chính** (dạng gạch đầu dòng ngắn)
            * **Ghi chú cho giáo viên** (nói gì thêm, gợi ý hỏi học sinh, tổ chức hoạt động)

            Khuyến khích:

            * Có slide dành cho **hoạt động nhóm**
            * Có câu hỏi giúp học sinh **trình bày – phản biện**
            * Thể hiện được **năng lực được rèn luyện** (tư duy, giao tiếp, hợp tác…)

            ---

            ## Input example

            Lớp dạy: {{TÊN_LỚP}}, trình độ {{MỨC_ĐỘ_HỌC_SINH}}.
            Điều kiện lớp học: có máy chiếu, bảng phụ.

            Yêu cầu cụ thể:

            * Tập trung giúp học sinh phân biệt **câu là mệnh đề và không phải mệnh đề**
            * Nội dung slide về **mệnh đề phủ định, kéo theo, đảo** phải có ví dụ gần gũi
            * Có **1–2 slide hoạt động nhóm**
            * Cuối bài có **3–5 câu hỏi trắc nghiệm nhanh** để củng cố

            ---

            ## Output format

            Xuất kết quả dưới dạng **dàn ý slide chi tiết**, theo cấu trúc:

            ### Slide 1: {{Tiêu đề}}

            * Nội dung:

              * …
              * …
            * Ghi chú GV:

              * …

            ### Slide 2: {{Tiêu đề}}

            * Nội dung:

              * …
            * Ghi chú GV:

              * …

            *(Tiếp tục cho toàn bộ bộ slide)*

            ---

            ## Constraint

            * Không chép nguyên văn sách giáo khoa.
            * Không đưa nội dung vượt phạm vi bài học.
            * Không nhồi chữ; mỗi slide tối đa **5–6 gạch đầu dòng**, mỗi gạch không quá 1 dòng.
            * Tổng số slide phù hợp với **{{SỐ_TIẾT}} tiết học** (khoảng 15–25 slide cho 1 tiết).
            * Ngôn ngữ rõ ràng, trực quan, dễ hiểu với học sinh.
            * Nội dung slide phải hỗ trợ **hoạt động học**, không thay thế lời giảng của giáo viên.

            ---
            """;

    public static final String TEST_TEMPLATE = """

            ---

            ## Context

            Bạn là **giáo viên THPT tại Việt Nam**, am hiểu **Chương trình Giáo dục Phổ thông 2018**, có kinh nghiệm xây dựng **đề kiểm tra và công cụ đánh giá** theo định hướng phát triển phẩm chất và năng lực học sinh.

            Nhiệm vụ của bạn là thiết kế **trọn bộ tài liệu kiểm tra – đánh giá**, bao gồm:

            * Đề kiểm tra
            * Đáp án chi tiết
            * Thang điểm
            * Rubric đánh giá

            dựa trên **ma trận đề đã được xác định trước**.

            Thông tin bài kiểm tra:

            * **Môn học:** {{TÊN_MÔN_HỌC}}
            * **Khối lớp:** {{KHỐI_LỚP}}
            * **Phạm vi kiến thức:** {{CHƯƠNG / CHỦ_ĐỀ / BÀI_HỌC}}
            * **Hình thức kiểm tra:** {{15_PHÚT / 1_TIẾT / GIỮA_KÌ / CUỐI_KÌ}}
            * **Thời gian làm bài:** {{SỐ_PHÚT}} phút
            * **Ma trận đề:** {{DÁN_HOẶC_TÓM_TẮT_MA_TRẬN}}

            **Mục tiêu đánh giá:**
            {{MÔ_TẢ_MỤC_TIÊU_ĐÁNH_GIÁ}}

            **Đối tượng học sinh:**
            {{MỨC_ĐỘ_CHUNG_CỦA_LỚP}}

            ---

            ## Instruction

            Thiết kế **một bộ tài liệu kiểm tra hoàn chỉnh**, bám sát ma trận đề, đảm bảo:

            * đúng chuẩn kiến thức – kĩ năng,
            * đúng tỉ lệ mức độ nhận thức,
            * phù hợp thời gian làm bài,
            * có thể sử dụng **trực tiếp trong thực tế giảng dạy**.

            Bộ tài liệu gồm **4 phần rõ ràng, tách biệt** như sau:

            ---

            ## PHẦN A. ĐỀ KIỂM TRA

            ### 1. Thông tin chung

            * Tên trường (có thể để trống)
            * Môn học – lớp
            * Hình thức kiểm tra
            * Thời gian làm bài
            * Hướng dẫn chung cho học sinh (cách làm bài, ghi đáp án, không sử dụng tài liệu…)

            ---

            ### 2. Nội dung đề kiểm tra

            Xây dựng đề theo đúng cấu trúc đã nêu trong ma trận, ví dụ:

            * **Phần I. Trắc nghiệm khách quan** (… câu, … điểm)
            * **Phần II. Tự luận / Thực hành / Viết** (… câu, … điểm)

            Với **mỗi câu hỏi**, cần thể hiện rõ:

            * Số thứ tự câu
            * Nội dung câu hỏi
            * Mức độ nhận thức *(NB / TH / VD / VDC)*
            * Số điểm

            #### Yêu cầu riêng:

            * Trắc nghiệm:

              * 4 phương án (A, B, C, D), chỉ 1 phương án đúng
              * Phương án nhiễu hợp lí, không đánh đố
            * Tự luận / bài tập:

              * Yêu cầu rõ ràng
              * Có thể chia ý nhỏ
              * Phù hợp thời gian làm bài

            ---

            ## PHẦN B. ĐÁP ÁN

            ### 1. Đáp án trắc nghiệm

            * Liệt kê **đáp án đúng cho từng câu** (theo bảng hoặc danh sách).

            ### 2. Đáp án tự luận / bài tập

            * Trình bày **hướng dẫn giải hoặc ý chính cần đạt**.
            * Không cần trình bày quá dài, nhưng đủ để giáo viên chấm thống nhất.
            * Chỉ rõ các bước chính hoặc luận điểm cần có.

            ---

            ## PHẦN C. THANG ĐIỂM

            Xây dựng **thang điểm chi tiết**, đảm bảo tổng điểm toàn bài là **10 điểm** (hoặc quy đổi tương đương).

            ### Yêu cầu:

            * Phân điểm rõ ràng cho từng câu, từng ý.
            * Phù hợp với mức độ nhận thức của câu hỏi.
            * Có thể chấm **linh hoạt nhưng thống nhất** giữa các giáo viên.

            Ví dụ:

            * Câu 1: 0,5 điểm
            * Câu 2:

              * Ý a: 0,5 điểm
              * Ý b: 0,5 điểm

            ---

            ## PHẦN D. RUBRIC ĐÁNH GIÁ

            Thiết kế **rubric đánh giá** cho bài kiểm tra, tập trung vào **năng lực và chất lượng thực hiện của học sinh**, đặc biệt với câu hỏi tự luận / vận dụng.

            ### Rubric cần thể hiện:

            * **Tiêu chí đánh giá**, ví dụ:

              * Mức độ đúng kiến thức
              * Lập luận / trình bày logic
              * Sử dụng thuật ngữ / kí hiệu đúng (nếu có)
              * Khả năng vận dụng / liên hệ

            * **Mức độ đạt được**, ví dụ:

              * Hoàn thành tốt
              * Hoàn thành
              * Chưa hoàn thành

            * **Mô tả cụ thể cho từng mức**, ngắn gọn, dễ áp dụng khi chấm.

            Rubric phải:

            * Phù hợp trình độ học sinh THPT
            * Dùng được cho **chấm điểm và nhận xét**

            ---

            ## Input example

            Lớp dạy: {{TÊN_LỚP}}, trình độ {{MỨC_ĐỘ_HỌC_SINH}}.

            Yêu cầu cụ thể:

            * Hình thức: {{5_CÂU_TRẮC_NGHIỆM + 1_CÂU_TỰ_LUẬN}}
            * Trắc nghiệm tập trung vào: {{NỘI_DUNG_TRỌNG_TÂM_TRẮC_NGHIỆM}}
            * Tự luận tập trung vào: {{NỘI_DUNG_TRỌNG_TÂM_TỰ_LUẬN}}
            * Không đưa câu hỏi vượt quá mức trung bình – khá của lớp

            ---

            ## Output format

            Xuất kết quả theo **thứ tự sau**, trình bày rõ ràng:

            1. **ĐỀ KIỂM TRA**
            2. **ĐÁP ÁN**
            3. **THANG ĐIỂM**
            4. **RUBRIC ĐÁNH GIÁ**

            Mỗi phần có tiêu đề rõ ràng, dễ tách riêng để in hoặc lưu trữ.

            ---

            ## Constraint

            * Bám sát **ma trận đề đã cho**, không tự ý thay đổi tỉ lệ.
            * Không chép nguyên văn sách giáo khoa.
            * Không đưa kiến thức ngoài phạm vi đã học.
            * Ngôn ngữ chuẩn mực, rõ ràng, phù hợp học sinh THPT.
            * Đề, đáp án, thang điểm và rubric phải **nhất quán với nhau**.
            * Có thể sử dụng **trực tiếp trong thực tế giảng dạy và kiểm tra**.

            ---

            """;

    public static final String TEST_MATRIX_TEMPLATE = """

            ---

            ## Context

            Bạn là **giáo viên THPT tại Việt Nam**, am hiểu **Chương trình Giáo dục Phổ thông 2018**, có kinh nghiệm xây dựng **ma trận đề kiểm tra – đánh giá kết quả học tập** theo định hướng phát triển phẩm chất và năng lực học sinh.

            Bài kiểm tra cần thiết kế ma trận có thông tin sau:

            * **Môn học:** {{TÊN_MÔN_HỌC}}
            * **Khối lớp:** {{KHỐI_LỚP}}
            * **Chương / Chủ đề / Bài học:** {{PHẠM_VI_KIỂM_TRA}}
            * **Hình thức kiểm tra:** {{15_PHÚT / 1_TIẾT / GIỮA_KÌ / CUỐI_KÌ}}
            * **Thời gian làm bài:** {{SỐ_PHÚT}} phút
            * **Đối tượng học sinh:** {{MỨC_ĐỘ_CHUNG_CỦA_LỚP}}

            **Mục tiêu đánh giá của bài kiểm tra:**
            {{MÔ_TẢ_MỤC_TIÊU_ĐÁNH_GIÁ}}

            **Chuẩn kiến thức – kĩ năng cần đánh giá:**
            {{LIỆT_KÊ_CÁC_NỘI_DUNG_KIẾN_THỨC_CHÍNH}}

            ---

            ## Instruction

            Thiết kế **một ma trận đề kiểm tra hoàn chỉnh**, đúng quy định hiện hành của Bộ GD&ĐT, bảo đảm **đánh giá được cả kiến thức và năng lực học sinh**.

            Ma trận cần thể hiện rõ:

            ---

            ### 1. Cấu trúc ma trận đề

            Ma trận được xây dựng theo:

            * **Nội dung kiến thức** (hàng)
            * **Mức độ nhận thức** (cột), gồm:

              * Nhận biết
              * Thông hiểu
              * Vận dụng
              * *(Vận dụng cao – chỉ dùng khi phù hợp hình thức kiểm tra)*

            ---

            ### 2. Yêu cầu phân bố mức độ nhận thức

            Phân bố câu hỏi/điểm số **hợp lí**, phù hợp đối tượng học sinh:

            * Nhận biết: khoảng **30%**
            * Thông hiểu: khoảng **40%**
            * Vận dụng: khoảng **30%**
            * Vận dụng cao: **không bắt buộc**, chỉ sử dụng khi kiểm tra 1 tiết trở lên

            *(Có thể điều chỉnh nhẹ tùy đặc thù môn học, nhưng phải giải thích ngắn gọn nếu lệch chuẩn.)*

            ---

            ### 3. Nội dung thể hiện trong ma trận

            Với **mỗi ô của ma trận**, cần nêu:

            * Số câu hỏi
            * Dạng câu hỏi (trắc nghiệm / tự luận / thực hành / kết hợp)
            * Số điểm hoặc tỉ lệ %
            * Mô tả ngắn gọn yêu cầu cần đánh giá (hành vi, năng lực học sinh)

            Khuyến khích:

            * Gắn mỗi nội dung với **năng lực môn học** được đánh giá
            * Thể hiện rõ mức độ yêu cầu (nhận biết, giải thích, vận dụng, liên hệ…)

            ---

            ### 4. Ghi chú định hướng ra đề

            * Ma trận phải **phù hợp thời gian làm bài**.
            * Không đánh giá nội dung chưa học.
            * Không thiên lệch quá mức về ghi nhớ máy móc.
            * Có cơ sở để xây dựng đề kiểm tra và đáp án sau này.

            ---

            ## Input example

            Lớp dạy: {{TÊN_LỚP}}, trình độ {{MỨC_ĐỘ_HỌC_SINH}}.

            Yêu cầu cụ thể:

            * Phạm vi kiểm tra: {{TÊN_BÀI / CHỦ_ĐỀ}}
            * Hình thức: {{SỐ_CÂU_TRẮC_NGHIỆM}} câu trắc nghiệm + {{SỐ_CÂU_TỰ_LUẬN}} câu tự luận
            * Ưu tiên đánh giá:

              * {{NỘI_DUNG_TRỌNG_TÂM_1}}
              * {{NỘI_DUNG_TRỌNG_TÂM_2}}
            * Không đưa câu hỏi quá khó so với mặt bằng chung của lớp

            ---

            ## Output format

            Xuất kết quả dưới dạng **ma trận đề kiểm tra**, trình bày rõ ràng, ví dụ:

            ### 1. Ma trận đề kiểm tra

            | Nội dung / Mức độ | Nhận biết | Thông hiểu | Vận dụng | Tổng |
            | ----------------- | --------- | ---------- | -------- | ---- |
            | Nội dung 1        | …         | …          | …        | …    |
            | Nội dung 2        | …         | …          | …        | …    |
            | **Tổng**          | …         | …          | …        | …    |

            ### 2. Thuyết minh ma trận

            * Giải thích ngắn gọn cách phân bố mức độ nhận thức.
            * Nêu định hướng ra đề tương ứng với ma trận.

            ---

            ## Constraint

            * Tuân thủ định hướng đánh giá của **GDPT 2018**.
            * Không đưa kiến thức ngoài phạm vi đã học.
            * Tỉ lệ mức độ nhận thức phải hợp lí, không mất cân đối.
            * Ngôn ngữ chuẩn mực, rõ ràng, dễ hiểu với giáo viên.
            * Ma trận có thể dùng **trực tiếp để ra đề kiểm tra**.

            ---

            """;

    public static final String GROUP_ACTIVITY_TEMPLATE = """

            ---

            ## Context

            Bạn là **giáo viên THPT tại Việt Nam**, am hiểu **Chương trình Giáo dục Phổ thông 2018**, có kinh nghiệm tổ chức **hoạt động học tập theo hình thức hợp tác nhóm**, lấy học sinh làm trung tâm.

            Nhiệm vụ của bạn là **thiết kế các hoạt động nhóm cụ thể, khả thi**, phục vụ cho **một bài học / một chủ đề / một phần của bài học**, giúp học sinh:

            * chủ động chiếm lĩnh kiến thức,
            * rèn luyện năng lực học tập và năng lực đặc thù môn học,
            * phát triển kĩ năng hợp tác, giao tiếp, phản biện.

            Thông tin đầu vào cho hoạt động nhóm:

            * **Môn học:** {{TÊN_MÔN_HỌC}}
            * **Khối lớp:** {{KHỐI_LỚP}}
            * **Bài học / Chủ đề:** {{TÊN_BÀI_HỌC / CHỦ_ĐỀ}}
            * **Vị trí hoạt động trong tiết học:**
              {{KHỞI_ĐỘNG / HÌNH_THÀNH_KIẾN_THỨC / LUYỆN_TẬP / VẬN_DỤNG}}
            * **Thời lượng dự kiến:** {{SỐ_PHÚT}} phút
            * **Số lượng học sinh / lớp:** {{SĨ_SỐ_LỚP}}
            * **Hình thức chia nhóm:** {{NHÓM_4 / NHÓM_6 / LINH_HOẠT}}
            * **Điều kiện lớp học:** {{CÓ_MÁY_CHIẾU / BẢNG_PHỤ / PHIẾU_HT / KHÔNG_THIẾT_BỊ}}

            ---

            ## Instruction

            Thiết kế **một hoặc nhiều hoạt động nhóm**, đảm bảo:

            * phù hợp vị trí trong tiến trình bài học,
            * phù hợp thời lượng,
            * học sinh **thực sự có việc để làm**,
            * sản phẩm nhóm rõ ràng,
            * giáo viên dễ tổ chức và kiểm soát.

            Mỗi **hoạt động nhóm** cần trình bày đầy đủ các nội dung sau:

            ---

            ## 1. Tên hoạt động

            * Ngắn gọn, gợi mở nội dung học tập.
            * Phù hợp tâm lí học sinh THPT.

            ---

            ## 2. Mục tiêu hoạt động

            Nêu rõ:

            * **Kiến thức** học sinh cần hình thành hoặc củng cố.
            * **Năng lực** được phát triển (chung và đặc thù môn học).
            * **Phẩm chất** (chăm chỉ, trách nhiệm, trung thực, hợp tác…).

            ---

            ## 3. Nhiệm vụ của học sinh

            * Mô tả **rõ ràng, cụ thể** yêu cầu giao cho nhóm.
            * Chia nhiệm vụ thành các bước nếu cần.
            * Ngôn ngữ dễ hiểu, tránh mơ hồ.

            Ví dụ:

            * Thảo luận, phân tích, so sánh, rút ra nhận xét.
            * Giải quyết một tình huống học tập.
            * Hoàn thành bảng, sơ đồ, câu trả lời ngắn.

            ---

            ## 4. Cách tổ chức hoạt động

            Nêu rõ:

            * Cách chia nhóm.
            * Vai trò trong nhóm (nhóm trưởng, thư kí, báo cáo viên…).
            * Hình thức làm việc: thảo luận, ghi phiếu, trình bày miệng, phản biện.

            ---

            ## 5. Thời lượng

            * Phân bổ thời gian hợp lí cho:

              * giao nhiệm vụ,
              * thảo luận nhóm,
              * trình bày – nhận xét – kết luận.

            ---

            ## 6. Sản phẩm học tập

            Xác định rõ:

            * Sản phẩm nhóm cần nộp hoặc trình bày là gì.
            * Hình thức sản phẩm:

              * bảng tổng hợp,
              * phiếu học tập,
              * câu trả lời miệng,
              * sơ đồ tư duy,
              * kết luận ngắn gọn.

            ---

            ## 7. Vai trò của giáo viên

            Mô tả cụ thể:

            * Cách giao nhiệm vụ.
            * Cách quan sát, hỗ trợ học sinh khi thảo luận.
            * Cách xử lí tình huống: nhóm lúng túng, tranh luận sai hướng…
            * Cách chốt kiến thức sau hoạt động.

            ---

            ## 8. Đánh giá trong hoạt động nhóm

            Nêu rõ:

            * Hình thức đánh giá:

              * quan sát,
              * nhận xét miệng,
              * đánh giá nhanh sản phẩm.
            * Tiêu chí đánh giá:

              * mức độ hoàn thành nhiệm vụ,
              * độ chính xác kiến thức,
              * tinh thần hợp tác,
              * khả năng trình bày.

            ---

            ## Input example

            Lớp: {{TÊN_LỚP}}, trình độ {{MỨC_ĐỘ_HỌC_SINH}}.

            Yêu cầu cụ thể:

            * Có ít nhất {{SỐ_HOẠT_ĐỘNG}} hoạt động nhóm.
            * Có hoạt động cho **tất cả thành viên đều tham gia**, tránh chỉ 1–2 học sinh làm.
            * Ưu tiên hoạt động:

              * thảo luận ngắn,
              * sản phẩm rõ ràng,
              * dễ chốt kiến thức.

            ---

            ## Output format

            Xuất kết quả theo cấu trúc:

            ### Hoạt động nhóm {{SỐ_THỨ_TỰ}}: {{TÊN_HOẠT_ĐỘNG}}

            1. Mục tiêu
            2. Nhiệm vụ của học sinh
            3. Cách tổ chức
            4. Thời lượng
            5. Sản phẩm học tập
            6. Vai trò của giáo viên
            7. Đánh giá hoạt động

            ---

            ## Constraint

            * Phù hợp **thời lượng trên lớp**, tránh hoạt động quá dài hoặc hình thức.
            * Không biến hoạt động nhóm thành “chép bài tập SGK”.
            * Nội dung phù hợp chương trình hiện hành.
            * Ngôn ngữ sư phạm, rõ ràng, dễ áp dụng.
            * Hoạt động phải **thực tế, khả thi trong lớp học THPT Việt Nam**.

            ---
            """;

    public static String getTemplate(PromptTask task) {
        return switch (task) {
            case LESSON_PLAN -> LESSON_PLAN_TEMPLATE;
            case SLIDE -> SLIDE_TEMPLATE;
            case TEST -> TEST_TEMPLATE;
            case TEST_MATRIX -> TEST_MATRIX_TEMPLATE;
            case GROUP_ACTIVITY -> GROUP_ACTIVITY_TEMPLATE;
        };
    }

    /**
     * Build system instruction for prompt generation
     */
    public static String buildSystemInstruction() {
        return """
                You are an experienced high-school teacher assistant and curriculum designer,
                familiar with secondary education standards and pedagogical best practices in Vietnam.
                                
                Your task is to generate a complete, structured prompt based on the provided template
                and the uploaded curriculum/reference document.
                                
                CRITICAL: Your response MUST be in valid JSON format with exactly these 5 fields:
                {
                  "instruction": "...",
                  "context": "...",
                  "input_example": "...",
                  "output_format": "...",
                  "constraints": "..."
                }
                                
                Extract relevant information from the uploaded document to fill in placeholder variables
                (e.g., {{TÊN_MÔN_HỌC}}, {{KHỐI_LỚP}}, etc.) in the template.
                                
                Do NOT include markdown formatting, backticks, or any preamble. Return ONLY the JSON object.
                """;
    }
}

