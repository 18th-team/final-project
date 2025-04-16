// 바닐라스크립트
document.addEventListener(
    //페이지 로드 후 실행.DOMContentLoaded는 HTML 다 로드되면 실행.
    'DOMContentLoaded', () => {

        //나이 검증
        const ageInput = document.getElementById('ageRestriction');
        ageInput.addEventListener('input', () => {
            let age = parseInt(ageInput.value);
            if (age < 20) {
                ageInput.value = 20;//나이가 20미만이면 20으로 맞추기
                ageInput.classList.add('is-invalid')//그리고 'is-invalid'클래스를 추가하여 invalid-feedback 클래스 문구 보이게 하기
            } else {
                ageInput.classList.remove('is-invalid')
            }
        })

        //파일미리보기
        let selectedFiles = []; //선택된 파일들 비우기
        const fileInput = document.getElementById('clubFile'); //파일 선택
        const preview = document.getElementById('preview'); //미리보기캔버스

        //파일 업로딩시 로직(파일선택은 change 바뀌니깐 )
        fileInput.addEventListener('change', (event) => {

            //! 먼저초기화
            selectedFiles = [];
            preview.innerHTML = '';
            //업로딩한 파일들(다중) 저장하기
            const files = event.target.files; //사용자가 선택한 **파일 객체들의 리스트(FileList)**를 의미합니다.

            //files들 검사하기, files이 없으면?
            if (!files || files.length === 0) {
                fileInput.classList.remove('is-valid');
                fileInput.classList.add('is-invalid');
                return;
            }

            //file 처리하기
            for (let file of files) {

                //img타입 확인
                if (!file.type.startsWith('image/')) {
                    alert('이미지만 업로딩 해주세요!🖼️ ');
                    updateFileInput();
                    continue;
                }
                //파일 읽기 (Base64).
                const reader = new FileReader();
                reader.onload = (e) => {//파일을 읽어오는 동안 비동기 처리 진행,
                    const img = new Image();//<img> 태그를 만들지 않고도 JavaScript 코드 내에서 이미지 파일을 다룰 수 있게 해주는 객체예요.
                    img.onload = () => {//이미지가 브라우저 메모리 안에서 완전히 로드된 후 실행할 작업을 정의
                        if (img.naturalWidth > 900 || img.naturalHeight > 900) {
                            alert(`${file.name}은 가로세로 900px 초과입니다! 다시 선택해주세요`);
                            return;
                        }
                        selectedFiles.push(file);//정상적인 파일 배열에 push
                        updateFileInput();

                        //미리보기 띄워주기
                        const container = document.createElement('div');
                        container.className = 'preview-item d-inline-block position-relative me-2 mb-2';
                        const imgElement = document.createElement('img')
                        imgElement.src = e.target.result;
                        imgElement.style.maxWidth = '200px';
                        imgElement.style.maxHeight = '200px';
                        imgElement.style.objectFit = 'cover';
                        //삭제버튼
                        const deleteBtn = document.createElement('button');
                        deleteBtn.className = 'btn btn-danger btn-sm position-absolute top-0 end-0';
                        deleteBtn.innerHTML = 'X';
                        deleteBtn.style.lineHeight = '1';
                        deleteBtn.addEventListener('click', () => {
                            selectedFiles.splice(selectedFiles.indexOf(file), 1); //파일 목록에서 삭제, 배열에서 ~번째에서 ~개 삭제하기 로직임.splice가. 그래서 배열중에서 file이 들어간 인덱스번호를 찾고 거기서부터 몇개 삭제할지 초이스하는 것.
                            container.remove();
                            updateFileInput();
                            fileInput.classList.toggle('is-valid', selectedFiles.length > 0); //검증상태 바꾸기
                            fileInput.classList.toggle('is-invalid', selectedFiles.length === 0);
                        })


                        //요소조립
                        container.appendChild(imgElement)
                        container.appendChild(deleteBtn)
                        preview.appendChild(container)
                    };
                    img.src = e.target.result;//FileReader가 읽은 Base64 데이터를 이미지로 설정
                };
                reader.readAsDataURL(file);

            }


        })
//정상적인 파일 selectedFiles배열에 동기화시키는 함수
        //DataTransfer 는 필수적이다. <input type="file">의 files 속성은 FileList 객체로, 읽기 전용이야.
        //해당 파일 리스트들을 직접적으로 삭제하려면 에러나거나 바뀌지가 않아
        //그래서 동기화 시켜주는 DataTransfer 도구를 사용해야함.
        function updateFileInput() {
            const dataTransfer = new DataTransfer();
            //* DataTransfer 는 브라우저가 파일 목록을 관리하는 도구,장바구니 같은 존재
            selectedFiles.forEach(file => dataTransfer.items.add(file));
            //selectedFiles 저장된 사진 배열들을 하나씩 꺼내어서 DataTransfer의 item 부분에 하나씩 저장해
            //dataTransfer.files: 최종 파일 목록 (FileList 형태).
            fileInput.files = dataTransfer.files;
        }
    }
);