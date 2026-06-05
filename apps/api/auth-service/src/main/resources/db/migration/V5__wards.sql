-- V5: Bảng xã/phường/thị trấn cho form địa chỉ KYC
-- Dữ liệu theo đơn vị hành chính sau sáp nhập 2025 (NQ 202/2025/QH15)
-- province_code khớp với client-side PROVINCES trong vietnamAddress.ts

CREATE TABLE wards (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    province_code VARCHAR(20)  NOT NULL COMMENT 'Mã tỉnh/thành (khớp với client PROVINCES)',
    name          VARCHAR(200) NOT NULL COMMENT 'Tên xã/phường/thị trấn',
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_wards_province (province_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- Hà Nội
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('HN','An Khánh'),('HN','Ba Vì'),('HN','Bách Khoa'),('HN','Bạch Đằng'),
('HN','Bạch Mai'),('HN','Biên Giang'),('HN','Bồ Đề'),('HN','Bùi Thị Xuân'),
('HN','Bưởi'),('HN','Cát Linh'),('HN','Cầu Dền'),('HN','Cầu Diễn'),
('HN','Chương Mỹ'),('HN','Cống Vị'),('HN','Cổ Nhuế I'),('HN','Cổ Nhuế II'),
('HN','Cự Khối'),('HN','Cửa Đông'),('HN','Cửa Nam'),('HN','Đại Kim'),
('HN','Đại Mỗ'),('HN','Đan Phượng'),('HN','Định Công'),('HN','Điện Biên'),
('HN','Đội Cấn'),('HN','Đồng Nhân'),('HN','Đồng Tâm'),('HN','Đồng Xuân'),
('HN','Đông Anh'),('HN','Đức Giang'),('HN','Đức Thắng'),('HN','Dịch Vọng'),
('HN','Dịch Vọng Hậu'),('HN','Dương Nội'),('HN','Đồng Mai'),('HN','Gia Lâm'),
('HN','Gia Thụy'),('HN','Giáp Bát'),('HN','Giảng Võ'),('HN','Giang Biên'),
('HN','Hà Cầu'),('HN','Hàng Bài'),('HN','Hàng Bạc'),('HN','Hàng Bông'),
('HN','Hàng Bồ'),('HN','Hàng Buồm'),('HN','Hàng Đào'),('HN','Hàng Gai'),
('HN','Hàng Mã'),('HN','Hàng Trống'),('HN','Hàng Bột'),('HN','Hạ Đình'),
('HN','Hoài Đức'),('HN','Hoàng Văn Thụ'),('HN','Khâm Thiên'),('HN','Khương Đình'),
('HN','Khương Mai'),('HN','Khương Trung'),('HN','Khương Thượng'),('HN','Kiến Hưng'),
('HN','Kim Liên'),('HN','Kim Mã'),('HN','La Khê'),('HN','Láng Hạ'),
('HN','Láng Thượng'),('HN','Lê Đại Hành'),('HN','Liên Mạc'),('HN','Liễu Giai'),
('HN','Lĩnh Nam'),('HN','Long Biên'),('HN','Lý Thái Tổ'),('HN','Mai Dịch'),
('HN','Mai Động'),('HN','Mê Linh'),('HN','Mễ Trì'),('HN','Minh Khai'),
('HN','Mỹ Đình I'),('HN','Mỹ Đình II'),('HN','Mỹ Đức'),('HN','Mộ Lao'),
('HN','Nam Đồng'),('HN','Ngã Tư Sở'),('HN','Nghĩa Đô'),('HN','Nghĩa Tân'),
('HN','Ngọc Hà'),('HN','Ngọc Khánh'),('HN','Ngọc Lâm'),('HN','Ngọc Thụy'),
('HN','Nguyễn Du'),('HN','Nguyễn Trung Trực'),('HN','Ngô Thì Nhậm'),
('HN','Nhân Chính'),('HN','Nhật Tân'),('HN','Ô Chợ Dừa'),('HN','Phan Chu Trinh'),
('HN','Phú Diễn'),('HN','Phú Đô'),('HN','Phú La'),('HN','Phú Thượng'),
('HN','Phú Xuyên'),('HN','Phúc Diễn'),('HN','Phúc La'),('HN','Phúc Lợi'),
('HN','Phúc Thọ'),('HN','Phúc Xá'),('HN','Phố Huế'),('HN','Phương Canh'),
('HN','Phương Liên'),('HN','Phương Liệt'),('HN','Phương Mai'),('HN','Quán Thánh'),
('HN','Quan Hoa'),('HN','Quang Trung'),('HN','Quảng An'),('HN','Quốc Oai'),
('HN','Quốc Tử Giám'),('HN','Sài Đồng'),('HN','Sóc Sơn'),('HN','Thái Thịnh'),
('HN','Thạch Bàn'),('HN','Thạch Thất'),('HN','Thành Công'),('HN','Thanh Lương'),
('HN','Thanh Nhàn'),('HN','Thanh Oai'),('HN','Thanh Trì'),('HN','Thanh Xuân Bắc'),
('HN','Thanh Xuân Nam'),('HN','Thanh Xuân Trung'),('HN','Tây Hồ'),('HN','Tây Mỗ'),
('HN','Tây Tựu'),('HN','Tân Mai'),('HN','Tương Mai'),('HN','Thịnh Liệt'),
('HN','Thịnh Quang'),('HN','Thổ Quan'),('HN','Thượng Cát'),('HN','Thượng Đình'),
('HN','Thượng Thanh'),('HN','Thụy Phương'),('HN','Thường Tín'),('HN','Trần Phú'),
('HN','Tràng Tiền'),('HN','Trần Hưng Đạo'),('HN','Trung Hòa'),('HN','Trung Liệt'),
('HN','Trung Phụng'),('HN','Trúc Bạch'),('HN','Trương Định'),('HN','Tứ Liên'),
('HN','Ứng Hòa'),('HN','Văn Chương'),('HN','Văn Miếu'),('HN','Văn Quán'),
('HN','Vạn Phúc'),('HN','Việt Hưng'),('HN','Vĩnh Hưng'),('HN','Vĩnh Phúc'),
('HN','Vĩnh Tuy'),('HN','Xuân Đỉnh'),('HN','Xuân La'),('HN','Xuân Phương'),
('HN','Xuân Tảo'),('HN','Yên Hòa'),('HN','Yên Nghĩa'),('HN','Yên Phụ'),
('HN','Yên Sở'),('HN','Yết Kiêu');

-- ─────────────────────────────────────────────────────────────────────────────
-- TP. Huế
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('HUE','A Lưới'),('HUE','An Cựu'),('HUE','An Đông'),('HUE','An Hòa'),
('HUE','An Tây'),('HUE','Hương Long'),('HUE','Hương Sơ'),('HUE','Hương Thủy'),
('HUE','Hương Trà'),('HUE','Kim Long'),('HUE','Nam Đông'),('HUE','Phong Điền'),
('HUE','Phú Bài'),('HUE','Phú Bình'),('HUE','Phú Cát'),('HUE','Phú Hiệp'),
('HUE','Phú Hậu'),('HUE','Phú Hội'),('HUE','Phú Lộc'),('HUE','Phú Nhuận'),
('HUE','Phú Thuận'),('HUE','Phú Vang'),('HUE','Phường Đúc'),('HUE','Quảng Điền'),
('HUE','Sịa'),('HUE','Tây Lộc'),('HUE','Thuận An'),('HUE','Thuận Hòa'),
('HUE','Thuận Lộc'),('HUE','Thuận Thành'),('HUE','Thủy Biều'),('HUE','Thủy Xuân'),
('HUE','Tứ Hạ'),('HUE','Trường An'),('HUE','Vinh Thanh'),('HUE','Vĩnh Lợi'),
('HUE','Vĩnh Ninh'),('HUE','Xuân Phú');

-- ─────────────────────────────────────────────────────────────────────────────
-- Đà Nẵng (gồm Quảng Nam)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('DN','An Hải Bắc'),('DN','An Hải Đông'),('DN','An Hải Tây'),('DN','An Khê'),
('DN','Bắc Trà My'),('DN','Bình Hiên'),('DN','Bình Thuận'),('DN','Chính Gián'),
('DN','Đại Lộc'),('DN','Điện Bàn'),('DN','Đông Giang'),('DN','Duy Xuyên'),
('DN','Hiệp Đức'),('DN','Hòa An'),('DN','Hòa Bắc'),('DN','Hòa Châu'),
('DN','Hòa Cường Bắc'),('DN','Hòa Cường Nam'),('DN','Hòa Hải'),('DN','Hòa Hiệp Bắc'),
('DN','Hòa Hiệp Nam'),('DN','Hòa Khánh Bắc'),('DN','Hòa Khánh Nam'),('DN','Hòa Khê'),
('DN','Hòa Liên'),('DN','Hòa Minh'),('DN','Hòa Nhơn'),('DN','Hòa Ninh'),
('DN','Hòa Phát'),('DN','Hòa Phong'),('DN','Hòa Phú'),('DN','Hòa Quý'),
('DN','Hòa Sơn'),('DN','Hòa Thọ Đông'),('DN','Hòa Thọ Tây'),('DN','Hòa Thuận Đông'),
('DN','Hòa Thuận Tây'),('DN','Hòa Tiến'),('DN','Hòa Xuân'),('DN','Hội An'),
('DN','Khuê Mỹ'),('DN','Khuê Trung'),('DN','Mân Thái'),('DN','Mỹ An'),
('DN','Nại Hiên Đông'),('DN','Nam Dương'),('DN','Nam Giang'),('DN','Nam Trà My'),
('DN','Núi Thành'),('DN','Phú Ninh'),('DN','Phước Mỹ'),('DN','Phước Ninh'),
('DN','Phước Sơn'),('DN','Quế Sơn'),('DN','Tam Kỳ'),('DN','Tam Thuận'),
('DN','Tân Chính'),('DN','Tây Giang'),('DN','Thạc Gián'),('DN','Thạch Thang'),
('DN','Thanh Bình'),('DN','Thăng Bình'),('DN','Thọ Quang'),('DN','Thuận Phước'),
('DN','Tiên Phước'),('DN','Vĩnh Trung'),('DN','Xuân Hà');

-- ─────────────────────────────────────────────────────────────────────────────
-- Hải Phòng (gồm Hải Dương)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('HP','An Biên'),('HP','An Dương'),('HP','An Lão'),('HP','Bắc Sơn'),
('HP','Bình Giang'),('HP','Cát Dài'),('HP','Cát Hải'),('HP','Cầu Đất'),
('HP','Cầu Tre'),('HP','Cẩm Giàng'),('HP','Chí Linh'),('HP','Đổng Quốc Bình'),
('HP','Đồng Hòa'),('HP','Dư Hàng'),('HP','Dương Kinh'),('HP','Đồ Sơn'),
('HP','Gia Lộc'),('HP','Gia Viên'),('HP','Hải An'),('HP','Hải Tân'),
('HP','Hàng Kênh'),('HP','Hoàng Văn Thụ'),('HP','Hồ Nam'),('HP','Kiến An'),
('HP','Kiến Thụy'),('HP','Kim Thành'),('HP','Kinh Môn'),('HP','Lạc Viên'),
('HP','Lạch Tray'),('HP','Lam Sơn'),('HP','Lê Lợi'),('HP','Lê Thanh Nghị'),
('HP','Máy Chai'),('HP','Máy Tơ'),('HP','Minh Khai'),('HP','Nam Sách'),
('HP','Nam Sơn'),('HP','Nghĩa Xá'),('HP','Ngọc Châu'),('HP','Nguyễn Trãi'),
('HP','Nhị Châu'),('HP','Niệm Nghĩa'),('HP','Ninh Giang'),('HP','Phan Bội Châu'),
('HP','Phạm Ngũ Lão'),('HP','Phù Liễn'),('HP','Quán Toan'),('HP','Sở Dầu'),
('HP','Thanh Bình'),('HP','Thanh Hà'),('HP','Thanh Miện'),('HP','Thủy Nguyên'),
('HP','Tiên Lãng'),('HP','Tràng Minh'),('HP','Trần Nguyên Hãn'),('HP','Trần Thành Ngọ'),
('HP','Trại Chuối'),('HP','Tứ Kỳ'),('HP','Tứ Minh'),('HP','Thượng Lý'),
('HP','Vạn Mỹ'),('HP','Việt Hòa'),('HP','Vĩnh Bảo'),('HP','Vĩnh Niệm');

-- ─────────────────────────────────────────────────────────────────────────────
-- TP. Hồ Chí Minh (gồm Bình Dương + Bà Rịa - Vũng Tàu)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
-- Quận 1
('HCM','Bến Nghé'),('HCM','Bến Thành'),('HCM','Cầu Kho'),('HCM','Cầu Ông Lãnh'),
('HCM','Cô Giang'),('HCM','Đa Kao'),('HCM','Nguyễn Cư Trinh'),('HCM','Nguyễn Thái Bình'),
('HCM','Phạm Ngũ Lão'),('HCM','Tân Định'),
-- TP. Thủ Đức
('HCM','An Khánh'),('HCM','An Lợi Đông'),('HCM','Bình Chiểu'),('HCM','Bình Thọ'),
('HCM','Bình Trưng Đông'),('HCM','Bình Trưng Tây'),('HCM','Cát Lái'),
('HCM','Hiệp Bình Chánh'),('HCM','Hiệp Bình Phước'),('HCM','Hiệp Phú'),
('HCM','Linh Chiểu'),('HCM','Linh Đông'),('HCM','Linh Tây'),('HCM','Linh Trung'),
('HCM','Linh Xuân'),('HCM','Long Bình'),('HCM','Long Phước'),('HCM','Long Thạnh Mỹ'),
('HCM','Long Trường'),('HCM','Phú Hữu'),('HCM','Phước Bình'),('HCM','Phước Long A'),
('HCM','Phước Long B'),('HCM','Tăng Nhơn Phú A'),('HCM','Tăng Nhơn Phú B'),
('HCM','Tam Bình'),('HCM','Tam Phú'),('HCM','Thảo Điền'),('HCM','Thủ Thiêm'),
('HCM','Trường Thọ'),('HCM','Trường Thanh'),
-- Tân Phú
('HCM','Hiệp Tân'),('HCM','Hòa Thạnh'),('HCM','Phú Thạnh'),('HCM','Phú Thọ Hòa'),
('HCM','Phú Trung'),('HCM','Sơn Kỳ'),('HCM','Tân Quý'),('HCM','Tân Sơn Nhì'),
('HCM','Tân Thành'),('HCM','Tân Thới Hòa'),('HCM','Tây Thạnh'),
-- Bình Tân
('HCM','An Lạc'),('HCM','An Lạc A'),('HCM','Bình Hưng Hòa'),('HCM','Bình Hưng Hòa A'),
('HCM','Bình Hưng Hòa B'),('HCM','Bình Trị Đông'),('HCM','Bình Trị Đông A'),
('HCM','Bình Trị Đông B'),('HCM','Tân Tạo'),('HCM','Tân Tạo A'),
-- Huyện
('HCM','Bình Chánh'),('HCM','Cần Giờ'),('HCM','Củ Chi'),('HCM','Hóc Môn'),('HCM','Nhà Bè'),
-- Quận khác (dùng tên quận)
('HCM','Quận 3'),('HCM','Quận 4'),('HCM','Quận 5'),('HCM','Quận 6'),
('HCM','Quận 7'),('HCM','Quận 8'),('HCM','Quận 10'),('HCM','Quận 11'),('HCM','Quận 12'),
('HCM','Gò Vấp'),('HCM','Phú Nhuận'),('HCM','Bình Thạnh'),('HCM','Tân Bình'),
-- Bình Dương cũ
('HCM','Bắc Tân Uyên'),('HCM','Bến Cát'),('HCM','Dầu Tiếng'),('HCM','Dĩ An'),
('HCM','Phú Giáo'),('HCM','Tân Uyên'),('HCM','Thủ Dầu Một'),('HCM','Thuận An'),
-- Bà Rịa - Vũng Tàu cũ
('HCM','Bà Rịa'),('HCM','Châu Đức'),('HCM','Côn Đảo'),('HCM','Đất Đỏ'),
('HCM','Long Điền'),('HCM','Phú Mỹ'),('HCM','Vũng Tàu'),('HCM','Xuyên Mộc');

-- ─────────────────────────────────────────────────────────────────────────────
-- Cần Thơ (gồm Hậu Giang + Sóc Trăng)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('CT','An Bình'),('CT','An Cư'),('CT','An Hòa'),('CT','An Khánh'),
('CT','An Lạc'),('CT','An Nghiệp'),('CT','An Phú'),('CT','Bình Thủy'),
('CT','Cái Khế'),('CT','Cái Răng'),('CT','Châu Thành A'),('CT','Cờ Đỏ'),
('CT','Cù Lao Dung'),('CT','Giá Rai'),('CT','Hòa Bình'),('CT','Hồng Dân'),
('CT','Hưng Lợi'),('CT','Kế Sách'),('CT','Long Mỹ'),('CT','Long Phú'),
('CT','Mỹ Tú'),('CT','Mỹ Xuyên'),('CT','Ngã Bảy'),('CT','Ngã Năm'),
('CT','Ninh Kiều'),('CT','Ô Môn'),('CT','Phụng Hiệp'),('CT','Phong Điền'),
('CT','Phước Long'),('CT','Sóc Trăng'),('CT','Tân An'),('CT','Thạnh Trị'),
('CT','Thốt Nốt'),('CT','Thới Bình'),('CT','Thới Lai'),('CT','Trần Đề'),
('CT','Vị Thanh'),('CT','Vị Thủy'),('CT','Vĩnh Châu'),('CT','Vĩnh Thạnh'),
('CT','Xuân Khánh');

-- ─────────────────────────────────────────────────────────────────────────────
-- An Giang (gồm Kiên Giang)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('AG','An Biên'),('AG','An Minh'),('AG','An Phú'),('AG','Châu Đốc'),
('AG','Châu Phú'),('AG','Châu Thành'),('AG','Chợ Mới'),('AG','Giang Thành'),
('AG','Giồng Riềng'),('AG','Gò Quao'),('AG','Hà Tiên'),('AG','Hòn Đất'),
('AG','Kiên Hải'),('AG','Kiên Lương'),('AG','Long Xuyên'),('AG','Phú Quốc'),
('AG','Rạch Giá'),('AG','Tân Châu'),('AG','Tân Hiệp'),('AG','Thoại Sơn'),
('AG','Tịnh Biên'),('AG','Tri Tôn'),('AG','U Minh Thượng'),('AG','Vĩnh Thuận');

-- ─────────────────────────────────────────────────────────────────────────────
-- Bắc Ninh (gồm Bắc Giang)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('BN','Đáp Cầu'),('BN','Đại Phúc'),('BN','Gia Bình'),('BN','Hiệp Hòa'),
('BN','Kinh Bắc'),('BN','Lạng Giang'),('BN','Lục Nam'),('BN','Lục Ngạn'),
('BN','Lương Tài'),('BN','Ninh Xá'),('BN','Quế Võ'),('BN','Sơn Động'),
('BN','Suối Hoa'),('BN','Tân Yên'),('BN','Thị Cầu'),('BN','Tiên Du'),
('BN','Tiền An'),('BN','TP. Bắc Giang'),('BN','Thuận Thành'),('BN','Từ Sơn'),
('BN','Vân Dương'),('BN','Việt Yên'),('BN','Vũ Ninh'),('BN','Yên Phong'),('BN','Yên Thế');

-- ─────────────────────────────────────────────────────────────────────────────
-- Cà Mau (gồm Bạc Liêu)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('CM','Cái Nước'),('CM','Đầm Dơi'),('CM','Đông Hải'),('CM','Giá Rai'),
('CM','Hòa Bình'),('CM','Hồng Dân'),('CM','Năm Căn'),('CM','Ngọc Hiển'),
('CM','Phú Tân'),('CM','Phước Long'),('CM','Thành phố Bạc Liêu'),
('CM','Thới Bình'),('CM','Tân Thành'),('CM','Trần Văn Thời'),
('CM','U Minh'),('CM','Vĩnh Lợi');

-- ─────────────────────────────────────────────────────────────────────────────
-- Cao Bằng
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('CB','Bảo Lạc'),('CB','Bảo Lâm'),('CB','Đề Thám'),('CB','Hà Quảng'),
('CB','Hạ Lang'),('CB','Hòa An'),('CB','Hòa Chung'),('CB','Hợp Giang'),
('CB','Ngọc Xuân'),('CB','Nguyên Bình'),('CB','Phục Hòa'),('CB','Quảng Hòa'),
('CB','Sông Bằng'),('CB','Sông Hiến'),('CB','Tân Giang'),('CB','Thạch An'),
('CB','Thông Nông'),('CB','Trà Lĩnh'),('CB','Trùng Khánh');

-- ─────────────────────────────────────────────────────────────────────────────
-- Đắk Lắk (gồm Phú Yên)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('DL','Buôn Đôn'),('DL','Buôn Hồ'),('DL','Cư Kuin'),('DL','Cư M''gar'),
('DL','Đắk Mil'),('DL','Đắk R''lấp'),('DL','Đắk Song'),('DL','Đông Hòa'),
('DL','Đồng Xuân'),('DL','Ea H''leo'),('DL','Ea Kar'),('DL','Ea Súp'),
('DL','Krông Bông'),('DL','Krông Búk'),('DL','Krông Năng'),('DL','Krông Pắk'),
('DL','Lắk'),('DL','M''Đrắk'),('DL','Phú Hòa'),('DL','Sông Hinh'),
('DL','Sơn Hòa'),('DL','Tây Hòa'),('DL','TP. Buôn Ma Thuột'),('DL','TP. Tuy Hòa'),
('DL','Tuy An');

-- ─────────────────────────────────────────────────────────────────────────────
-- Điện Biên
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('DB','Điện Biên'),('DB','Điện Biên Đông'),('DB','Mường Ảng'),('DB','Mường Chà'),
('DB','Mường Nhé'),('DB','Nậm Pồ'),('DB','TP. Điện Biên Phủ'),
('DB','Tủa Chùa'),('DB','Tuần Giáo'),('DB','TX. Mường Lay');

-- ─────────────────────────────────────────────────────────────────────────────
-- Đồng Nai (gồm Bình Phước)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('DNai','Bù Đăng'),('DNai','Bù Đốp'),('DNai','Bù Gia Mập'),('DNai','Cẩm Mỹ'),
('DNai','Chơn Thành'),('DNai','Định Quán'),('DNai','Đồng Phú'),('DNai','Hớn Quản'),
('DNai','Long Khánh'),('DNai','Long Thành'),('DNai','Lộc Ninh'),('DNai','Nhơn Trạch'),
('DNai','Phước Long'),('DNai','Tân Phú'),('DNai','TP. Biên Hòa'),('DNai','TP. Đồng Xoài'),
('DNai','Thống Nhất'),('DNai','TX. Bình Long'),('DNai','Trảng Bom'),
('DNai','Vĩnh Cửu'),('DNai','Xuân Lộc');

-- ─────────────────────────────────────────────────────────────────────────────
-- Đồng Tháp (gồm Tiền Giang)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('DT','Cái Bè'),('DT','Cao Lãnh (huyện)'),('DT','Châu Thành'),('DT','Chợ Gạo'),
('DT','Gò Công Đông'),('DT','Gò Công Tây'),('DT','Hồng Ngự'),('DT','Lai Vung'),
('DT','Lấp Vò'),('DT','Sa Đéc'),('DT','Tam Nông'),('DT','Tân Hồng'),
('DT','Tân Phước'),('DT','Thanh Bình'),('DT','Tháp Mười'),('DT','TP. Cao Lãnh'),
('DT','TP. Mỹ Tho'),('DT','TX. Cai Lậy'),('DT','TX. Gò Công');

-- ─────────────────────────────────────────────────────────────────────────────
-- Gia Lai (gồm Bình Định)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('GL','Chư Pǎh'),('GL','Chư Prông'),('GL','Chư Sê'),('GL','Đắk Đoa'),
('GL','Đắk Pơ'),('GL','Hoài Ân'),('GL','Ia Grai'),('GL','Ia Pa'),
('GL','K''Bang'),('GL','Krông Pa'),('GL','Mang Yang'),('GL','Phù Cát'),
('GL','Phú Thiện'),('GL','Phù Mỹ'),('GL','TP. Pleiku'),('GL','TP. Quy Nhơn'),
('GL','Tây Sơn'),('GL','Tuy Phước'),('GL','TX. An Khê'),('GL','TX. An Nhơn'),
('GL','TX. Ayun Pa'),('GL','TX. Hoài Nhơn'),('GL','Vân Canh'),('GL','Vĩnh Thạnh');

-- ─────────────────────────────────────────────────────────────────────────────
-- Hà Tĩnh
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('HT','Cẩm Xuyên'),('HT','Can Lộc'),('HT','Đức Thọ'),('HT','Hương Khê'),
('HT','Hương Sơn'),('HT','Kỳ Anh'),('HT','Lộc Hà'),('HT','Nghi Xuân'),
('HT','Thạch Hà'),('HT','TP. Hà Tĩnh'),('HT','TX. Hồng Lĩnh'),
('HT','TX. Kỳ Anh'),('HT','Vũ Quang');

-- ─────────────────────────────────────────────────────────────────────────────
-- Hưng Yên (gồm Thái Bình)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('HY','Ân Thi'),('HY','Đông Hưng'),('HY','Hưng Hà'),('HY','Khoái Châu'),
('HY','Kiến Xương'),('HY','Kim Động'),('HY','Mỹ Hào'),('HY','Phù Cừ'),
('HY','Quỳnh Phụ'),('HY','Thái Thụy'),('HY','Tiên Lữ'),('HY','Tiền Hải'),
('HY','TP. Hưng Yên'),('HY','TP. Thái Bình'),('HY','Văn Giang'),
('HY','Văn Lâm'),('HY','Vũ Thư'),('HY','Yên Mỹ');

-- ─────────────────────────────────────────────────────────────────────────────
-- Khánh Hòa (gồm Ninh Thuận)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('KH','Bác Ái'),('KH','Diên Khánh'),('KH','Khánh Sơn'),('KH','Khánh Vĩnh'),
('KH','Lộc Thọ'),('KH','Ngọc Hiệp'),('KH','Ninh Hải'),('KH','Ninh Phước'),
('KH','Ninh Sơn'),('KH','Phước Hải'),('KH','Phước Hòa'),('KH','Phước Tân'),
('KH','Phước Tiến'),('KH','Phương Sài'),('KH','Phương Sơn'),('KH','Tân Lập'),
('KH','Thuận Bắc'),('KH','Thuận Nam'),('KH','TP. Cam Ranh'),
('KH','TP. Nha Trang'),('KH','TP. Phan Rang - Tháp Chàm'),
('KH','Trường Sa'),('KH','TX. Ninh Hòa'),('KH','Vạn Ninh'),
('KH','Vạn Thắng'),('KH','Vạn Thạnh'),('KH','Vĩnh Hải'),('KH','Vĩnh Hòa'),
('KH','Vĩnh Nguyên'),('KH','Vĩnh Phước'),('KH','Vĩnh Thọ'),
('KH','Vĩnh Trường'),('KH','Xương Huân');

-- ─────────────────────────────────────────────────────────────────────────────
-- Lai Châu
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('LC','Mường Tè'),('LC','Nậm Nhùn'),('LC','Phong Thổ'),('LC','Sìn Hồ'),
('LC','Tam Đường'),('LC','Tân Uyên'),('LC','Than Uyên'),('LC','TX. Lai Châu');

-- ─────────────────────────────────────────────────────────────────────────────
-- Lạng Sơn
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('LS','Bắc Sơn'),('LS','Bình Gia'),('LS','Cao Lộc'),('LS','Chi Lăng'),
('LS','Đình Lập'),('LS','Hữu Lũng'),('LS','Lộc Bình'),('LS','TP. Lạng Sơn'),
('LS','Tràng Định'),('LS','Văn Lãng'),('LS','Văn Quan');

-- ─────────────────────────────────────────────────────────────────────────────
-- Lào Cai (gồm Yên Bái)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('LCai','Bảo Thắng'),('LCai','Bảo Yên'),('LCai','Bát Xát'),('LCai','Lục Yên'),
('LCai','Mù Cang Chải'),('LCai','Mường Khương'),('LCai','Si Ma Cai'),
('LCai','TP. Lào Cai'),('LCai','TP. Yên Bái'),('LCai','Trạm Tấu'),
('LCai','Trấn Yên'),('LCai','TX. Nghĩa Lộ'),('LCai','TX. Sa Pa'),
('LCai','Văn Bàn'),('LCai','Văn Chấn'),('LCai','Văn Yên'),('LCai','Yên Bình');

-- ─────────────────────────────────────────────────────────────────────────────
-- Lâm Đồng (gồm Đắk Nông + Bình Thuận)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('LĐ','Bắc Bình'),('LĐ','Bảo Lâm'),('LĐ','Cát Tiên'),('LĐ','Cư Jút'),
('LĐ','Đam Rông'),('LĐ','Đắk Glong'),('LĐ','Đắk Mil'),('LĐ','Đắk R''lấp'),
('LĐ','Đắk Song'),('LĐ','Đạ Huoai'),('LĐ','Đạ Tẻh'),('LĐ','Di Linh'),
('LĐ','Đơn Dương'),('LĐ','Đức Linh'),('LĐ','Đức Trọng'),('LĐ','Hàm Tân'),
('LĐ','Hàm Thuận Bắc'),('LĐ','Hàm Thuận Nam'),('LĐ','Krông Nô'),
('LĐ','Lạc Dương'),('LĐ','Lâm Hà'),('LĐ','Phú Quý'),('LĐ','Tánh Linh'),
('LĐ','TP. Bảo Lộc'),('LĐ','TP. Đà Lạt'),('LĐ','TP. Gia Nghĩa'),
('LĐ','TP. Phan Thiết'),('LĐ','Tuy Đức'),('LĐ','Tuy Phong'),('LĐ','TX. La Gi');

-- ─────────────────────────────────────────────────────────────────────────────
-- Nghệ An
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('NA','Anh Sơn'),('NA','Bến Thủy'),('NA','Con Cuông'),('NA','Cửa Nam'),
('NA','Diễn Châu'),('NA','Đô Lương'),('NA','Đội Cung'),('NA','Hà Huy Tập'),
('NA','Hưng Bình'),('NA','Hưng Dũng'),('NA','Hưng Nguyên'),('NA','Hưng Phúc'),
('NA','Kỳ Sơn'),('NA','Lê Lợi'),('NA','Lê Mao'),('NA','Nam Đàn'),
('NA','Nghi Lộc'),('NA','Nghĩa Đàn'),('NA','Quang Trung'),('NA','Quế Phong'),
('NA','Quỳ Châu'),('NA','Quỳ Hợp'),('NA','Quỳnh Lưu'),('NA','Tân Kỳ'),
('NA','Thanh Chương'),('NA','TP. Vinh'),('NA','TX. Cửa Lò'),
('NA','TX. Hoàng Mai'),('NA','TX. Thái Hòa'),('NA','Tương Dương'),('NA','Yên Thành');

-- ─────────────────────────────────────────────────────────────────────────────
-- Ninh Bình (gồm Hà Nam + Nam Định)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('NB','Bình Lục'),('NB','Duy Tiên'),('NB','Gia Viễn'),('NB','Giao Thủy'),
('NB','Hải Hậu'),('NB','Hoa Lư'),('NB','Kim Bảng'),('NB','Kim Sơn'),
('NB','Lý Nhân'),('NB','Mỹ Lộc'),('NB','Nam Trực'),('NB','Nghĩa Hưng'),
('NB','Nho Quan'),('NB','Thanh Liêm'),('NB','TP. Nam Định'),('NB','TP. Ninh Bình'),
('NB','TP. Phủ Lý'),('NB','Trực Ninh'),('NB','TX. Tam Điệp'),('NB','Vụ Bản'),
('NB','Xuân Trường'),('NB','Yên Khánh'),('NB','Yên Mô'),('NB','Ý Yên');

-- ─────────────────────────────────────────────────────────────────────────────
-- Phú Thọ (gồm Vĩnh Phúc + Hòa Bình)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('PT','Bình Xuyên'),('PT','Cao Phong'),('PT','Cẩm Khê'),('PT','Đà Bắc'),
('PT','Đoan Hùng'),('PT','Hạ Hòa'),('PT','Kim Bôi'),('PT','Lạc Sơn'),
('PT','Lạc Thủy'),('PT','Lâm Thao'),('PT','Lập Thạch'),('PT','Lương Sơn'),
('PT','Mai Châu'),('PT','Phù Ninh'),('PT','Sông Lô'),('PT','Tam Dương'),
('PT','Tam Đảo'),('PT','Tam Nông'),('PT','Tân Lạc'),('PT','Tân Sơn'),
('PT','Thanh Ba'),('PT','Thanh Sơn'),('PT','Thanh Thủy'),('PT','TP. Hòa Bình'),
('PT','TP. Việt Trì'),('PT','TP. Vĩnh Yên'),('PT','TX. Phú Thọ'),
('PT','TX. Phúc Yên'),('PT','Vĩnh Tường'),('PT','Yên Lạc'),('PT','Yên Lập'),('PT','Yên Thủy');

-- ─────────────────────────────────────────────────────────────────────────────
-- Quảng Ngãi (gồm Kon Tum)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('QNgai','Ba Tơ'),('QNgai','Bình Sơn'),('QNgai','Đắk Glei'),('QNgai','Đắk Hà'),
('QNgai','Đắk Tô'),('QNgai','Ia H''Drai'),('QNgai','Kon Plông'),('QNgai','Kon Rẫy'),
('QNgai','Lý Sơn'),('QNgai','Minh Long'),('QNgai','Mộ Đức'),('QNgai','Nghĩa Hành'),
('QNgai','Ngọc Hồi'),('QNgai','Sa Thầy'),('QNgai','Sơn Hà'),('QNgai','Sơn Tây'),
('QNgai','Sơn Tinh'),('QNgai','TP. Kon Tum'),('QNgai','TP. Quảng Ngãi'),
('QNgai','Trà Bồng'),('QNgai','Tu Mơ Rông'),('QNgai','Tư Nghĩa'),('QNgai','TX. Đức Phổ');

-- ─────────────────────────────────────────────────────────────────────────────
-- Quảng Ninh
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('QNinh','Ba Chẽ'),('QNinh','Bãi Cháy'),('QNinh','Bạch Đằng'),('QNinh','Bình Liêu'),
('QNinh','Cao Thắng'),('QNinh','Đại Yên'),('QNinh','Đầm Hà'),('QNinh','Giếng Đáy'),
('QNinh','Hà Khánh'),('QNinh','Hà Khẩu'),('QNinh','Hà Lầm'),('QNinh','Hà Phong'),
('QNinh','Hà Trung'),('QNinh','Hà Tu'),('QNinh','Hải Hà'),('QNinh','Hồng Gai'),
('QNinh','Hùng Thắng'),('QNinh','Tiên Yên'),('QNinh','TP. Cẩm Phả'),
('QNinh','TP. Hạ Long'),('QNinh','TP. Móng Cái'),('QNinh','TX. Uông Bí'),
('QNinh','Vân Đồn'),('QNinh','Việt Hưng'),('QNinh','Yết Kiêu');

-- ─────────────────────────────────────────────────────────────────────────────
-- Quảng Trị (gồm Quảng Bình)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('QT','Bố Trạch'),('QT','Cam Lộ'),('QT','Cồn Cỏ'),('QT','Dakrông'),
('QT','Gio Linh'),('QT','Hải Lăng'),('QT','Hướng Hóa'),('QT','Lệ Thủy'),
('QT','Minh Hóa'),('QT','Quảng Ninh'),('QT','Quảng Trạch'),
('QT','TP. Đông Hà'),('QT','TP. Đồng Hới'),('QT','Triệu Phong'),
('QT','Tuyên Hóa'),('QT','TX. Quảng Trị'),('QT','Vĩnh Linh');

-- ─────────────────────────────────────────────────────────────────────────────
-- Sơn La
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('SL','Bắc Yên'),('SL','Mai Sơn'),('SL','Mộc Châu'),('SL','Mường La'),
('SL','Phù Yên'),('SL','Quỳnh Nhai'),('SL','Sông Mã'),('SL','Sốp Cộp'),
('SL','TP. Sơn La'),('SL','Thuận Châu'),('SL','Vân Hồ'),('SL','Yên Châu');

-- ─────────────────────────────────────────────────────────────────────────────
-- Tây Ninh (gồm Long An)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('TN','Bến Cầu'),('TN','Bến Lức'),('TN','Cần Đước'),('TN','Cần Giuộc'),
('TN','Châu Thành'),('TN','Dương Minh Châu'),('TN','Đức Hòa'),('TN','Đức Huệ'),
('TN','Gò Dầu'),('TN','Hòa Thành'),('TN','Mộc Hóa'),('TN','Tân Biên'),
('TN','Tân Châu'),('TN','Tân Hưng'),('TN','Tân Thạnh'),('TN','Tân Trụ'),
('TN','Thạnh Hóa'),('TN','Thủ Thừa'),('TN','TP. Tân An'),('TN','TP. Tây Ninh'),
('TN','Trảng Bàng'),('TN','TX. Kiến Tường'),('TN','Vĩnh Hưng');

-- ─────────────────────────────────────────────────────────────────────────────
-- Thái Nguyên (gồm Bắc Kạn)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('TNguyên','Ba Bể'),('TNguyên','Bạch Thông'),('TNguyên','Chợ Đồn'),
('TNguyên','Chợ Mới'),('TNguyên','Định Hóa'),('TNguyên','Đại Từ'),
('TNguyên','Đồng Hỷ'),('TNguyên','Na Rì'),('TNguyên','Ngân Sơn'),
('TNguyên','Pác Nặm'),('TNguyên','Phú Bình'),('TNguyên','Phú Lương'),
('TNguyên','TP. Bắc Kạn'),('TNguyên','TP. Thái Nguyên'),
('TNguyên','TX. Phổ Yên'),('TNguyên','TX. Sông Công'),('TNguyên','Võ Nhai');

-- ─────────────────────────────────────────────────────────────────────────────
-- Thanh Hóa
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('THoa','Bá Thước'),('THoa','Cẩm Thủy'),('THoa','Đông Sơn'),('THoa','Hà Trung'),
('THoa','Hậu Lộc'),('THoa','Hoằng Hóa'),('THoa','Lang Chánh'),('THoa','Mường Lát'),
('THoa','Nga Sơn'),('THoa','Ngọc Lặc'),('THoa','Như Thanh'),('THoa','Như Xuân'),
('THoa','Nông Cống'),('THoa','Quan Hóa'),('THoa','Quan Sơn'),('THoa','Quảng Xương'),
('THoa','Thạch Thành'),('THoa','Thiệu Hóa'),('THoa','Thọ Xuân'),('THoa','Thường Xuân'),
('THoa','Tĩnh Gia'),('THoa','TP. Thanh Hóa'),('THoa','Triệu Sơn'),
('THoa','TX. Bỉm Sơn'),('THoa','TX. Sầm Sơn'),('THoa','Vĩnh Lộc'),('THoa','Yên Định');

-- ─────────────────────────────────────────────────────────────────────────────
-- Tuyên Quang (gồm Hà Giang)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('TQ','Bắc Mê'),('TQ','Bắc Quang'),('TQ','Chiêm Hóa'),('TQ','Đồng Văn'),
('TQ','Hàm Yên'),('TQ','Hoàng Su Phì'),('TQ','Lâm Bình'),('TQ','Mèo Vạc'),
('TQ','Na Hang'),('TQ','Quản Bạ'),('TQ','Quang Bình'),('TQ','Sơn Dương'),
('TQ','TP. Hà Giang'),('TQ','TP. Tuyên Quang'),('TQ','Vị Xuyên'),
('TQ','Xín Mần'),('TQ','Yên Minh'),('TQ','Yên Sơn');

-- ─────────────────────────────────────────────────────────────────────────────
-- Vĩnh Long (gồm Bến Tre + Trà Vinh)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO wards (province_code, name) VALUES
('VL','Ba Tri'),('VL','Bình Đại'),('VL','Bình Minh'),('VL','Bình Tân'),
('VL','Càng Long'),('VL','Cầu Kè'),('VL','Cầu Ngang'),('VL','Châu Thành'),
('VL','Chợ Lách'),('VL','Duyên Hải'),('VL','Giồng Trôm'),('VL','Long Hồ'),
('VL','Mang Thít'),('VL','Mỏ Cày Bắc'),('VL','Mỏ Cày Nam'),('VL','Tam Bình'),
('VL','Thạnh Phú'),('VL','Tiểu Cần'),('VL','TP. Bến Tre'),('VL','TP. Trà Vinh'),
('VL','TP. Vĩnh Long'),('VL','Trà Cú'),('VL','Trà Ôn'),('VL','Vũng Liêm');
