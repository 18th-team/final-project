document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('clubForm');
    const addressInput = document.getElementById('search-query');
    const hiddenFeedback = document.getElementById('hidden-fields-feedback');

    form.addEventListener('submit', (event) => {
        if (!form.checkValidity()) {
            event.preventDefault();
            event.stopPropagation();
            form.classList.add('was-validated');
        } else {
            const hiddenFields = ['location', 'locationTitle', 'latitude', 'longitude'];
            let hasError = false;

            hiddenFields.forEach(field => {
                const input = document.getElementById(field);
                if (!input.value) {
                    hasError = true;
                    input.classList.add('is-invalid');
                } else {
                    input.classList.remove('is-invalid');
                }
            });

            if (hasError) {
                event.preventDefault();
                event.stopPropagation();
                addressInput.classList.add('is-invalid');
                hiddenFeedback.style.display = 'block';

            } else {
                addressInput.classList.remove('is-invalid');
                hiddenFeedback.style.display = 'none';
            }
        }
    });


    // 주소 검색 버튼 클릭 시 피드백 초기화
    document.querySelector('button[onclick="searchAddress()"]').addEventListener('click', () => {
        addressInput.classList.remove('is-invalid');
        hiddenFeedback.style.display = 'none';
        filedFeedback.style.display = 'none';
    });
});