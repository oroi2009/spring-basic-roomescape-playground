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
    cell.colSpan = 4;
    cell.textContent = '예약 및 대기 내역이 없습니다.';
    return;
  }

  data.reservations.forEach(item => {
    const row = tableBody.insertRow();

    row.insertCell(0).textContent = item.theme;
    row.insertCell(1).textContent = item.date;
    row.insertCell(2).textContent = item.time;
    row.insertCell(3).textContent = item.status;
  });
}

function requestRead(endpoint) {
  return fetch(endpoint)
      .then(response => {
        if (response.status === 200) return response.json();
        throw new Error('Read failed');
      });
}
