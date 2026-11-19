# Stream Pocket · Android

Ứng dụng độc lập cho Android 10 trở lên, thiết bị ARM64. APK cũng chứa x86_64 để thử trên máy ảo. Không cần chạy webtool hay máy tính sau khi cài.

## Cài đặt

Tải **Stream-Pocket-1.1.6.apk** từ [GitHub Releases](https://github.com/zeusato/StreamPocket/releases/latest), mở trên điện thoại và cài đè bản cũ. Cho phép cài ứng dụng từ nguồn đó nếu Android hỏi. Bản release tắt chế độ debug và giữ chứng thư ký của các APK trước để cập nhật không mất dữ liệu; chưa phát hành qua Google Play.

Bản 1.1.4 trở về trước cần cài 1.1.5 thủ công một lần. Từ 1.1.5, app tự kiểm tra GitHub khi mở (tối đa một lần mỗi 24 giờ); cũng có nút **Cài đặt → Cập nhật ứng dụng → Kiểm tra**. Có bản mới sẽ hiện thông báo, nút tải và tiến trình. Giữ app mở khi tải APK; tải xong bấm **Cài đặt** và xác nhận trong trình cài Android. App kiểm tra SHA-256, mã phiên bản, package và chữ ký APK trước khi mở trình cài. Có thể hủy tải/thử lại; cài đè giữ thiết lập và mật khẩu.

Cho phép thông báo để theo dõi tải khi app chạy nền. Thành phẩm ở **Download/Stream Pocket**. Xiaomi có thể giới hạn tác vụ chạy nền theo cài đặt pin; nếu hàng đợi bị gián đoạn, mở app để xem trạng thái và thử lại.

## Bản 1.1.6 · Tải HLS song song

- Lấy tối đa 3 đoạn HLS đồng thời, ghi theo đúng thứ tự playlist. Giữ nguyên chất lượng, khóa/IV AES và byte range; hỗ trợ cả TS và init/fragment MP4 như trước.
- Chỉ giữ một cửa sổ 3 file đoạn tạm trên đĩa; dữ liệu đọc/giải mã qua bộ đệm, không giữ nguyên đoạn trong RAM. Không tải trước cả bộ phim.
- Khi hủy hoặc một đoạn lỗi, đóng các kết nối thuộc lượt tải đó; đợi worker dừng rồi dọn file tạm và đầu ra chưa hoàn chỉnh. Không hủy kết nối player đang xem.
- Tốc độ là tổng byte tải qua các luồng, trung bình trượt khoảng 3 giây, cập nhật mỗi 0,5 giây kể cả lúc mạng đứng; hiển thị KiB/s hoặc MiB/s.
- Giảm thời gian chờ giữa các đoạn; không bảo đảm đạt tốc độ cố định khi CDN/đường truyền bị giới hạn. Chưa đo trên điện thoại thật.

## Bản 1.1.5 · Cập nhật trong ứng dụng

- Tự phát hiện bản stable mới từ GitHub Releases; hiện phiên bản, dung lượng và ghi chú thay đổi.
- Tải APK với tiến trình/hủy/thử lại, xác minh toàn vẹn và chữ ký; dùng trình cài Android để người dùng xác nhận.
- Cập nhật dùng được ngay cả khi hai tab nguồn/tải đang khóa. Không tự cài ngầm hoặc yêu cầu gỡ app.
- Giữ khả năng nhận CDN động của 1.1.4.

## Bản 1.1.4 · Tự nhận CDN

- Không còn danh sách tên miền CDN Huangguo cố định trong app. Mỗi lần đọc danh mục/bộ phim, app nhận URL ảnh do trang cấp; mỗi lần xem/tải, app nhận URL luồng từ phiên API mới.
- Đọc playlist để nhận tiếp CDN của playlist con, audio, subtitle, init segment, đoạn video và khóa HLS. Tự xử lý chuyển hướng HTTPS; đường dẫn tương đối được tính theo vị trí playlist cuối sau chuyển hướng.
- Media3 và bộ tải cùng dùng OkHttp cùng quy tắc nhận CDN, không còn hai bộ kiểm tra tên miền khác nhau.
- Trang chính/nguồn nhanh tự theo chuyển hướng sang miền mới khi trang đích có cấu trúc nguồn hợp lệ; giao diện/toggle/link cập nhật theo miền thực tế. Các miền nguồn ban đầu vẫn dùng làm điểm khởi đầu khi mở lại app.
- URL được cấp quyền trong phiên tài nguyên native, không mở toàn bộ tên miền cho mọi tác vụ. Không chuyển header xác thực CDN sang miền khác; chặn HTTP, địa chỉ nội bộ và DNS trả IP nội bộ. Không lưu danh sách CDN vĩnh viễn.
- Nếu cấu hình giải mã ảnh thay đổi, app đọc lại cấu hình nguồn một lần khi giải mã lỗi. Nhận cả script cấu hình ảnh trên CDN được trang tham chiếu.
- Giữ mật khẩu và các chức năng của 1.1.3 khi cài đè. Đây là tự thích ứng khi URL/CDN thay đổi; thay đổi định dạng API, giao thức mã hóa hoặc miền khởi đầu ngừng hoạt động hoàn toàn vẫn có thể cần sửa app.

## Bản 1.1.3 · 19/11/2026

- Sửa lỗi ảnh và video Huangguo do nguồn đổi CDN: hỗ trợ pic.qnlycc.cn, yd-lphu-hls.tjyyjk.cn và các máy chủ tp[0-9]+.qnlycc.cn. Vẫn giới hạn HTTPS/tên miền hợp lệ và không chuyển header xác thực sang CDN khác.
- Nguồn nhanh cũ duhgxf.lbvrhyir.cc chuyển hướng 301 sang 1bb4a2.ipycpknh.cc. Toggle dùng đích mới; nhận cả link cũ lẫn mới. HuangguoAI vẫn là nguồn chính.
- Hai tab Thư viện nguồn / Hàng đợi yêu cầu mật khẩu. Lần đầu bật công tắc trong Cài đặt: tạo mật khẩu 6–128 ký tự và nhập lại. Các lần bật sau: nhập mật khẩu. Hủy hoặc sai mật khẩu không mở tab.
- Tắt công tắc hoặc khởi tạo lại màn hình/app sẽ khóa. Quay về từ player trong cùng phiên không phải nhập lại. Không lưu trạng thái đã mở khóa. Nguồn và thao tác tải được kiểm tra ở native bridge, không chỉ ẩn UI; công cụ/video local vẫn hoạt động, tác vụ đang chạy vẫn tiếp tục.
- Chỉ lưu verifier PBKDF2-HMAC-SHA256 (salt ngẫu nhiên, 210.000 vòng) trong vùng no-backup của app; không lưu mật khẩu nguyên văn. Sai 5 lần phải đợi 30 giây trong phiên. Không có chức năng đặt lại/khôi phục. Gỡ app và cài lại để reset; cài đè APK giữ mật khẩu.
- Nguồn thật đã trả ảnh giải mã được và playlist trên cả hai trang. Một nút CDN gặp lỗi TCP từ JVM Windows; kiểm tra playlist khi đó dùng curl với cùng header. Không tải toàn bộ video trong kiểm tra này. Điện thoại thật và hiệu năng phát chưa được xác minh; xem VERIFICATION.md.

## Bản 1.1.2

- Player bỏ header và tên video. Trước/sau tập nằm ở hàng nút giữa cùng tua; chụp ảnh và xoay nằm dưới cạnh cài đặt. Tốc độ phát nằm trong cài đặt. Các nút ẩn/hiện cùng bộ điều khiển, không phủ tối toàn màn hình.
- Trong lúc kéo tua, timeline và thời điểm đích luôn hiện trên lớp preview; nhả tay mới áp dụng.
- Video trên máy mặc định mới nhất trước theo ngày thêm vào MediaStore. Với thư mục do người dùng chọn, Android chỉ cung cấp ngày sửa đổi nên dùng ngày đó và thông báo rõ. Vẫn có lựa chọn sắp theo tên/số tập.
- Danh sách tập online có một ảnh bìa cạnh tiêu đề bộ phim, không thêm ảnh vào từng tập HuangguoAI. Bấm ảnh để thử lại nếu tải lỗi.
- Thời lượng từng tập lấy từ trackingEpisodes.drama_duration (giây); chỉ dùng duration của schema cho đúng tập đang mở khi cần. Hiển thị mm:ss hoặc h:mm:ss; thiếu dữ liệu ghi “Chưa rõ thời lượng”, không dò/tải từng luồng video.
- Build, lint, 14 JVM tests liên quan và UI mô phỏng cầu nối Android đã qua. Chưa chạy kiểm thử native trên điện thoại.

## Bản 1.1.1

- Preview khi tua video trên máy phủ toàn vùng phát, giữ tỷ lệ bằng FIT_CENTER. Mốc thời gian hiển thị ở dưới; độ sáng/âm lượng vẫn dùng thông báo giữa màn hình.
- Tăng giới hạn frame preview lên 1280×1280 (giữ tỷ lệ), giải mã tuần tự/gộp yêu cầu như trước. Thiếu bộ nhớ khi tạo preview sẽ bỏ ảnh đó, không làm văng app.
- Nhả tay mới áp dụng tua; hủy, chuyển video hoặc xoay màn hình dọn lớp preview để không che hình phát.
- Build/lint và 6 kiểm thử cử chỉ qua; chưa kiểm tra native trên điện thoại.

## Bản 1.1.0

Ứng dụng mặc định mở **Trình phát**, thanh dưới chỉ có **Trình phát / Công cụ**. Trong ⚙ Cài đặt, bật **Nguồn video & tải xuống** để hiện thêm **Thư viện nguồn / Hàng đợi**. Công tắc được nhớ trên máy; ẩn menu không hủy tác vụ đang chạy. Nén/ghép vẫn có tiến trình, hủy, thử lại và mở kết quả ngay trong Công cụ.

- Quét MediaStore sau khi được cấp quyền đọc video. Android 14+ hỗ trợ cả quyền truy cập một phần; có nút cấp quyền/chọn thêm video.
- Chọn và nhớ thư mục qua bộ chọn Android, gồm thư mục con. Thumbnail tải khi cuộn gần video, tìm tên, sắp theo số tập hoặc mới nhất, xem thêm mỗi 60 mục. Giới hạn mỗi lượt quét 10.000 video / 20.000 mục thư mục; có thông báo nếu bị giới hạn.
- Player chung cho file content URI và nguồn online. Trước/Sau theo danh sách đang xem; tự qua mục kế tiếp khi phát hết. Online lấy mới phiên phát khi chuyển tập.
- Vuốt ngang khóa hướng sau ngưỡng 12 dp; kéo hết chiều ngang tương ứng tối đa 90 giây, làm tròn mốc tua 0,1 giây và giới hạn trong thời lượng video. Chỉ seek khi nhả, CANCEL/đa chạm không áp dụng tua.
- Vuốt dọc: bên trái (tính từ điểm đặt tay) chỉnh sáng cửa sổ player, bên phải chỉnh âm lượng media. Không đổi hành động giữa một lượt kéo.
- Preview khi tua file trên máy: giải mã ảnh nhỏ trên worker, gộp yêu cầu liên tiếp. Online hiện thời điểm tua; chưa có ảnh preview HLS. Tốc độ xuất preview phụ thuộc codec/provider.
- Chụp khung hình hiển thị bằng PixelCopy, PNG ở Pictures/Stream Pocket, tối đa 1920 px cạnh dài. Nút Trước/Sau, Chụp, Tốc độ, Xoay ẩn/hiện cùng bộ điều khiển; vuốt ngang hàng nút nếu màn hình hẹp.
- Tốc độ phát 0,5×–2×; nhường audio focus và dừng khi tháo tai nghe.
- Playlist giữ trong RAM; nếu Android giết toàn bộ tiến trình thì cần mở lại video.

Build/lint, 29 JVM tests offline và kiểm thử UI đã qua; 3 kiểm thử mạng tùy chọn không chạy lại trong lượt này. Test Android cho MediaStore/thumbnail/lưu PNG đã biên dịch, chưa chạy do chưa có thiết bị kết nối. Chưa xác nhận cử chỉ, PixelCopy và preview trên Xiaomi thực tế.

## Bản 1.0.3

- Sửa lỗi cấu hình thumbnail trên Android: thay regex Object.freeze bằng JSONTokener. Regex cũ có dấu đóng ngoặc nhọn không escape mà Android ICU từ chối, dù JVM desktop chấp nhận.
- Giữ các sửa lỗi FFmpegKit, thumbnail data URL, nút xoay và thanh trạng thái của 1.0.2.
- 23 JVM tests, build và lint đều qua. Test Android cho parser đã biên dịch nhưng chưa chạy trên thiết bị. Cài đè bản cũ bằng APK cùng chữ ký.

## Bản 1.0.2

- Sửa WebView chặn ảnh JPEG data URL do chính app tạo ra; lỗi ảnh được hiện trên giao diện và lưu riêng trong chẩn đoán.
- Đóng gói smart-exception-java/common 0.2.1 mà FFmpegKit cần lúc khởi tạo. Hai thông báo thiếu Exceptions và FFmpegKitConfig có cùng nguyên nhân này.
- Nút xoay ẩn/hiện cùng thanh điều khiển player.
- Áp dụng khoảng cách thanh hệ thống lên container bên ngoài WebView để nội dung không chồng vào status bar.
- Cài đè bản 1.0.1. Đã kiểm tra build, lint, 20 JVM tests, UI và các lớp runtime trong APK; chưa chạy trên điện thoại thật.

## Bản 1.0.1

- Dark mode mặc định; player có nút Xoay ngang / Xoay dọc.
- Thumbnail lấy đúng URL cấu hình có phiên bản từ HTML; giải mã, thu nhỏ trước khi gửi sang giao diện. Có nút tải lại ảnh khi lỗi.
- Huangguo dùng phiên khách dx150-edge-v1 và POST /api/videos/{id}/episodes/{ep}/playback; dùng URL và header CDN mới từ phản hồi. API GET cũ hiện trả URL rỗng.
- Tải segment xuống file tạm và giải mã qua bộ đệm 64 KiB, không giữ toàn segment trong heap. Giới hạn cache ảnh, xử lý ảnh lần lượt, giảm tần suất lưu tiến trình.
- Kiểm tra dung lượng trống, bắt lỗi khởi động tải nền và thêm ⚙ → Chẩn đoán lỗi → Sao chép chẩn đoán.
- Chưa có log từ Xiaomi của người dùng để khẳng định nguyên nhân crash cụ thể. APK có cùng chữ ký với 1.0, có thể cài đè.

## Tính năng

- HuangguoAI: danh mục, ảnh, tìm kiếm, xem thêm, danh sách tập, tác giả, dán link phim, nguồn chính/nguồn nhanh.
- Porntasia: danh sách theo tag, từ khóa, phân trang, phiên khách mặc định. Không bắt nhập token.
- Token tài khoản là tùy chọn; chỉ lưu khi chọn nhớ token, mã hóa bằng Android Keystore. Có nút xóa token để về chế độ khách.
- Xem trực tiếp bằng Media3 ExoPlayer trong app.
- Tải từng tập hoặc hàng loạt; đánh số theo thứ tự tick; ghép một file theo đúng thứ tự. Mỗi lần giao việc chụp riêng danh sách đã chọn.
- Hàng đợi chạy qua foreground service, phần trăm, giai đoạn, tốc độ tải và thông báo hoàn tất; hủy, thử lại và mở thành phẩm.
- Tối ưu và ghép các file chọn từ điện thoại, đổi thứ tự, tùy chọn xóa nguồn sau khi lưu kết quả.
- Ghép tải đủ các tập rồi mới xử lý. File trung gian được dọn khi tác vụ kết thúc.

## Xử lý video

Giao diện là các asset cục bộ trong WebView. HTTP, HLS, file, player, hàng đợi và xử lý video chạy trong mã Android Java. Không nhúng Node.js hoặc khởi động server PC.

Media3 Transformer / MediaCodec mã hóa video bằng phần cứng: ưu tiên H.265 khi tối ưu, dùng H.264 nếu máy không có encoder H.265. Bộ chọn encoder chỉ nhận codec phần cứng. Máy không có encoder phù hợp báo lỗi thay vì âm thầm dùng CPU encode video.

Ghép các luồng có cùng thông số và cấu hình codec dùng stream copy. Nếu khác nhau, chuẩn hóa bằng phần cứng rồi ghép. FFmpegKit Min dùng để đọc thông tin, đổi container, xử lý âm thanh và nối luồng; không dùng x264/x265 CPU.

Tối ưu là mã hóa mất dữ liệu, có thể có sai khác nhỏ về hình. Giữ bản gốc nếu bản tối ưu không nhỏ hơn. Tốc độ, nhiệt độ và mức tiết kiệm phụ thuộc thiết bị và nguồn, chưa có benchmark Xiaomi 17T Pro.

## Phiên khách Porntasia

Kiểm tra ngày 14/11/2026: frontend dùng cookie `__cflb` nếu có, còn phiên khách gửi token rỗng. API initial không trả token khách. API danh sách trả 25 mục và một mẫu metadata HD trả playlist HLS hoàn chỉnh khi token rỗng.

App dùng đúng chế độ này; không tạo tài khoản, không giả token và không đăng nhập thay người dùng. Quyền xem một nội dung vẫn do nguồn quyết định. Nếu cần tài khoản, mở phần token tùy chọn.

## Build

Cần JDK 17, Android SDK Platform 35 và Build Tools 35.0.0. Đặt `JAVA_HOME`, `ANDROID_HOME`, hoặc mở dự án bằng Android Studio và để IDE cấu hình SDK. Lần đầu cần mạng để Gradle tải dependency.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

APK ở `app/build/outputs/apk/debug/app-debug.apk`. `Build APK.cmd` chạy cùng các tác vụ trên.

## Phát hành bản mới

1. Tăng cả `versionCode` và `versionName` trong `app/build.gradle`.
2. Chuẩn bị keystore riêng tại `.signing/stream-pocket.keystore`, hoặc đặt `STREAM_POCKET_KEYSTORE`, `STREAM_POCKET_STORE_PASSWORD`, `STREAM_POCKET_KEY_ALIAS`, `STREAM_POCKET_KEY_PASSWORD`. Giữ đúng khóa đã ký các bản cũ. Khóa không được đưa lên Git; sao lưu riêng ở nơi an toàn. Build release sẽ lỗi nếu không có khóa. Giá trị mặc định phục vụ khóa legacy hiện có; khi phát hành một ứng dụng độc lập khác, dùng khóa riêng của ứng dụng đó.
3. Chạy `gradlew.bat testReleaseUnitTest assembleRelease lintRelease`, `node tests-ui.mjs`, `python verify-apk.py app/build/outputs/apk/release/app-release.apk`; xác minh chữ ký và ZIP alignment bằng Android Build Tools.
4. Chạy `python prepare-release.py`. Script tạo APK, `update.json` và `SHA256SUMS.txt` trong `releases/v<version>/`.
5. Push mã nguồn và tag `v<version>`. Tạo GitHub Release dạng draft với đúng tag, tải lên đủ ba file, rồi publish stable/latest. Updater chỉ nhận APK đúng tên `Stream-Pocket-<version>.apk`, `update.json` khớp tag/versionCode/kích thước và SHA-256. Không sửa APK của một phiên bản đã phát hành; tăng phiên bản để thay thế.

Thư mục `releases/`, `.signing/`, kết quả build và thông tin SDK cục bộ đã được Git ignore. Android luôn yêu cầu người dùng xác nhận cài đặt; app không cài âm thầm.

## Kiểm tra

- `testDebugUnitTest`: phân trang, link nguồn, header CDN, HLS AES-128, byte range và fMP4.
- `node tests-ui.mjs`: kiểm tra giao diện bằng Chrome trên Windows; bridge Android được giả lập. Kiểm tra độ rộng 360/390/768, thứ tự tick, reset lựa chọn, ghép, chọn file và phiên khách.
- `connectedDebugAndroidTest`: kiểm tra trên thiết bị/emulator thật, gồm Keystore, FFmpeg và encoder. **Chưa chạy được trong môi trường hiện tại** do máy ảo bị crash khi boot; đã compile test APK.
- Xem `VERIFICATION.md` để biết phạm vi đã xác minh.

## Giới hạn bản đầu

- Android có thể dừng tác vụ nền; app báo gián đoạn, thử lại tải từ đầu, chưa resume từng segment.
- Chỉ HLS VOD hoàn chỉnh; không hỗ trợ DRM, SAMPLE-AES hoặc master playlist có luồng audio riêng.
- Quyền xóa file gốc phụ thuộc document provider. Nếu không xóa được, giữ nguồn và báo trạng thái.
- Tên phim chưa có bản dịch sẵn có thể mở Google Dịch qua nút Dịch tên. Không gửi token sang Google.
- Chưa kiểm tra trên điện thoại thật: playback, download trọn file, encoding phần cứng, SAF và hành vi chạy nền cần thử APK trên máy.
- Add-on và webtool PC được giữ nguyên.

Icon gốc: `artwork/icon.svg`, preview: `artwork/icon-preview.png`. Adaptive icon có foreground/background và phiên bản monochrome.
