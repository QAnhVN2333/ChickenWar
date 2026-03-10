# Issues

## Resolved (2026-03-10)
- [x] Thêm #comment hướng dẫn placeholder trong `config.yml` và `messages.yml`.
- [x] Cải thiện text UI/UX khi `/cw restart` và mở cược: hiển thị seed chi tiết từng bên (`seed_red`, `seed_blue`) và tổng (`seed_total`).
- [x] Sửa parser âm thanh để nhận cả 2 định dạng token:
  - `ENTITY_ENDER_DRAGON_GROWL`
  - `entity.ender_dragon.growl`
- [x] Thêm tab completion cho toàn bộ command `/cw`.

## Notes
- Đã build kiểm tra bằng Maven (`mvn -q -DskipTests package`) thành công.
- Nếu server đang dùng file cấu hình cũ trong thư mục plugin runtime, cần đồng bộ lại các comment/message mới theo `src/main/resources`.
