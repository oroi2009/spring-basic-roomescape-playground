const RESERVATION_API_ENDPOINT = '/reservations-mine';

document.addEventListener('DOMContentLoaded', () => {
  requestRead(RESERVATION_API_ENDPOINT)
      .then(render)
      .catch(error => console.error('Error fetching reservations:', error));
});

function render(data) {
  const tableBody = document.getElementById('table-body');
  tableBody.innerHTML = '';

  if (data.reservations.length === 0) {
    const cell = tableBody.insertRow().insertCell();
    cell.colSpan = 5;
    cell.textContent = '예약 및 대기 내역이 없습니다.';
    return;
  }

  data.reservations.forEach(item => {
    const row = tableBody.insertRow();

    row.insertCell(0).textContent = item.theme;
    row.insertCell(1).textContent = item.date;
    row.insertCell(2).textContent = item.time;
    row.insertCell(3).textContent = item.status;

    const actionCell = row.insertCell(4);
    if (item.waitingId !== null && item.waitingId !== undefined) {
      const cancelButton = document.createElement('button');
      cancelButton.textContent = '취소';
      cancelButton.className = 'btn btn-danger';
      cancelButton.onclick = function () {
        requestDeleteWaiting(item.waitingId)
            .then(() => window.location.reload())
            .catch(error => alert(error.message));
      };
      actionCell.appendChild(cancelButton);
    }
  });
}

function requestRead(endpoint) {
  return fetch(endpoint)
      .then(response => {
        if (response.status === 200) return response.json();
        throw new Error('Read failed');
      });
}

function requestDeleteWaiting(id) {
  return fetch('/waitings/' + id, { method: 'DELETE' })
      .then(response => {
        if (response.status === 204) return;
        return response.json()
            .catch(() => null)
            .then(error => {
              throw new Error(error?.message || '예약 대기 취소에 실패했습니다.');
            });
      });
}
