function orderStatusLabel(status, method) {
  if (status === 'CONFIRMED' && method === 'BANK_TRANSFER') return 'Đã nhận tiền và xác nhận đơn';
  if (status === 'DELIVERED') return method === 'COD' ? 'Đã thanh toán và nhận đơn' : 'Đã nhận hàng';
  return ({PENDING: 'Chờ cửa hàng xác nhận đơn', CONFIRMED: 'Đơn đã được xác nhận',
    PREPARING: 'Đang chuẩn bị nông sản', READY_FOR_PICKUP: 'Sẵn sàng nhận tại cửa hàng',
    SHIPPING: 'Đang giao hàng', CANCELLED: 'Đã hủy'})[status] || 'Đã cập nhật';
}
module.exports = {orderStatusLabel};
