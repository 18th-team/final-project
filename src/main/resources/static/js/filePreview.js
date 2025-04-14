
// * 나이검사 및 사진 미리보기
$(document).ready(function () {
    // Age Restriction 실시간 검증
    $('#ageRestriction').on('input', function () {
        let age = parseInt($(this).val());
        if (age < 20) {
            $(this).val(20);
            $(this).addClass('is-invalid');


        } else {
            $(this).removeClass('is-invalid');
        }
    });


    // 파일 선택 시 미리보기
    let selectedFiles = [];

    $('#clubFile').on('change', function (event) {
        let preview = $('#preview');
        preview.empty();
        selectedFiles = [];
        let files = event.target.files;
        let hasInvalidFile = false;

        if (files && files.length > 0) {
            $.each(files, function (index, file) {
                console.log(file.type)

                if (file.type.match('image.*')) {

                    let reader = new FileReader();
                    reader.onload = function (e) {
                        // 임시 이미지 객체로 크기 확인
                        let tempImg = new Image();
                        tempImg.src = e.target.result;
                        tempImg.onload = function () {
                            if (tempImg.naturalHeight > 800||tempImg.naturalWidth>800) {
                                hasInvalidFile = true;
                                return;
                            }

                            // 유효 파일 처리
                            selectedFiles.push(file);
                            let container = $('<div>').addClass('preview-item d-inline-block position-relative me-2 mb-2');
                            let img = $('<img>').attr('src', e.target.result)
                                .css({'max-width': '200px', 'max-height': '200px', 'object-fit': 'cover'});
                            let deleteBtn = $('<button>').addClass('btn btn-danger btn-sm position-absolute top-0 end-0')
                                .html('×')
                                .css({'line-height': '1'})
                                .on('click', function () {
                                    selectedFiles.splice(selectedFiles.indexOf(file), 1);
                                    container.remove();
                                    updateFileInput();
                                    if (selectedFiles.length === 0) {
                                        $('#clubFile').removeClass('is-valid').addClass('is-invalid');
                                    }
                                });
                            container.append(img).append(deleteBtn);
                            preview.append(container);
                        };
                    };
                    reader.readAsDataURL(file);
                } else {
                    hasInvalidFile = true;
                }
            });

            // 파일 처리 후
            setTimeout(() => {
                if (hasInvalidFile) {
                    alert('800px 이상의 이미지가 포함되어 있어요! 가로 or 세로길이가 800px 이하의 이미지만 업로드됩니다. 🖼️');
                }
                updateFileInput();
                if (selectedFiles.length > 0) {
                    $('#clubFile').removeClass('is-invalid').addClass('is-valid');
                } else {
                    $('#clubFile').removeClass('is-valid').addClass('is-invalid');
                }
            }, 100); // 비동기 로드 대기
        } else {
            $('#clubFile').removeClass('is-valid').addClass('is-invalid');
        }
    });

    function updateFileInput() {
        const input = document.getElementById('clubFile');
        const dataTransfer = new DataTransfer();
        selectedFiles.forEach(file => dataTransfer.items.add(file));
        input.files = dataTransfer.files;
    }
});