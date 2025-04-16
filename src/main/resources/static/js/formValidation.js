document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('clubForm');
    const hiddenFeedback = document.getElementById('hidden-fields-feedback');

    form.addEventListener('submit', (event) => {
        const hiddenFields = ['location', 'locationTitle', 'latitude', 'longitude'];
        let hasError = false;
        hiddenFields.forEach(field => {
            const input = document.getElementById(field);
            if (!input.value.trim()) {
                hasError = true;
            }
        });
        if (hasError) {
            event.preventDefault();
            event.stopPropagation();
            // 사용자에게 보이는 주소 입력창에 에러 표시
            hiddenFeedback.style.display = 'block';
        } else {
            hiddenFeedback.style.display = 'none';
        }

    });
});