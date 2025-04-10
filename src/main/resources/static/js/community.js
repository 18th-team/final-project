<script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>
$(function () {
    const token = $("meta[name='_csrf']").attr("content");
    const header = $("meta[name='_csrf_header']").attr("content");

    $(document).ajaxSend(function (e, xhr, options) {
        xhr.setRequestHeader(header, token);
    });
});

let msnry;

// Masonry 수동 초기화 (페이지 처음 로드될 때)
$(window).on('load', function () {
    imagesLoaded('#feed-container', function () {
        msnry = new Masonry('#feed-container', {
            itemSelector: '.col',
            percentPosition: true
        });
    });
});

let offset = 4;
const limit = 4;
const loadMoreBtn = document.getElementById("loadMoreBtn");

// "더보기" 버튼이 존재할 때만 이벤트 바인딩
if (loadMoreBtn) {
    loadMoreBtn.addEventListener("click", function () {
        $.ajax({
            url: "/post/feed/more",
            type: "GET",
            data: { offset, limit },
            success: function (posts) {
                if (posts.length === 0) {
                    $("#loadMoreBtn").hide();
                    return;
                }

                const newElems = [];

                posts.forEach(post => {
                    if (post.boardType !== "FEED") return;
                    const $newCard = $(`
                        <div class="col">
                            <div class="post-card">
                                <div class="d-flex align-items-center">
                                    <img src="${post.authorProfileImage || '/img/default-profile.png'}" class="rounded-circle me-2 img-fluid" style="width: 40px; height: 40px;">
                                    <div>
                                        <div class="post-author">${post.authorName}</div>
                                        <div class="post-time">${new Date(post.createDate).toLocaleString()}</div>
                                    </div>
                                </div>
                                <div class="mt-2">
                                    <img src="${post.imageURL}" class="img-fluid rounded mb-2" alt="피드 이미지" style="width: 100%; height:auto;">
                                    <h5>${post.title}</h5>
                                    <p class="mb-2">${post.content}</p>
                                    <div class="hashtags">
                                        ${
                        post.tags.split(',').map(tag =>
                            `<a href="/post/feed/list?keyword=${tag.trim()}" class="me-1 text-decoration-none text-muted hashtag">#${tag.trim()}</a>`
                        ).join('')
                    }
                                    </div>
                                </div>
                                <div class="icons mt-3">
                                    <span>🤍 0</span>
                                    <span class="ms-3">💬 0</span>
                                </div>
                            </div>
                        </div>
                    `);

                    $("#feed-container").append($newCard);
                    newElems.push($newCard[0]);
                });

                imagesLoaded('#feed-container', function () {
                    msnry.appended(newElems);
                    msnry.layout(); // 정확한 높이 계산
                });

                offset += posts.length;

                if (posts.length < limit) {
                    $("#loadMoreBtn").hide();
                }
            },
            error: function () {
                alert("피드를 더 불러오는 데 실패했습니다.");
            }
        });
    });
} else {
    console.log("더보기 버튼이 존재하지 않습니다.");
}



$(document).ready(function () {


    // 댓글 모달 열기
    $(".comment-toggle").on("click", function () {
        const postID = $(this).data("post-id");
        $("#commentPostID").val(postID);

        $(".comment-list").addClass("d-none");
        $(`#comment-list-${postID}`).removeClass("d-none");

        const modal = new bootstrap.Modal(document.getElementById('commentModal'));
        modal.show();
    });

    // 댓글 등록
    $("#commentForm").on("submit", function (e) {
        e.preventDefault();

        const postID = $("#commentPostID").val();
        const content = $("#commentContent").val();
        const csrfToken = $("input[name='_csrf']").val();

        $.ajax({
            url: "/comment",
            type: "POST",
            contentType: "application/x-www-form-urlencoded; charset=UTF-8",
            data: {
                postID: postID,
                content: content,
                _csrf: csrfToken
            },
            success: function () {
                const commentHtml = `
                        <strong>나</strong>
                        <div>${content}</div>
                        <div class="text-muted">${new Date().toLocaleString()}</div>
                        <hr>
                    `;
                $(`#comment-list-${postID}`).append(commentHtml);
                $("#commentContent").val(""); // 입력창 비우기
            },
            error: function () {
                alert("댓글 등록 실패");
            }
        });
    });
});

// 수정 버튼 클릭 시 입력창 토글
$(document).on("click", ".edit-comment-btn", function () {
    const commentId = $(this).data("comment-id");
    const $commentDiv = $(`#comment-${commentId}`);
    const originalContent = $commentDiv.find(".comment-content").text();

    const editForm = `
        <div class="edit-form mt-2">
            <textarea class="form-control edit-content" rows="2">${originalContent}</textarea>
            <button class="btn btn-sm btn-primary mt-1 submit-edit" data-comment-id="${commentId}">수정 완료</button>
        </div>`;

    $commentDiv.append(editForm);
});

$(document).on("click", ".submit-edit", function () {
    const commentId = $(this).data("comment-id");
    const newContent = $(this).siblings(".edit-content").val();
    const csrfToken = $("input[name='_csrf']").val();

    $.ajax({
        url: `/comment/edit/${commentId}`,
        type: "POST",
        data: {
            content: newContent,
            _csrf: csrfToken
        },
        success: function () {
            // 화면 업데이트
            const $commentBlock = $(`#comment-${commentId}`);
            $commentBlock.find(".comment-content").text(newContent);
            $commentBlock.find(".edit-form").remove();
        },
        error: function (xhr) {
            alert("수정 실패: " + xhr.responseText);
        }
    });
});

$(document).on("click", ".delete-comment-btn", function () {
    if (!confirm("삭제할까요?")) return;

    const commentId = $(this).data("comment-id");

    // 메타 태그에서 CSRF 토큰과 헤더 이름을 가져옴
    const csrfToken = $("meta[name='_csrf']").attr("content");
    const csrfHeader = $("meta[name='_csrf_header']").attr("content");

    $.ajax({
        url: `/comment/delete/${commentId}`,
        type: "POST",
        headers: {
            [csrfHeader]: csrfToken  // ← 헤더에 담기
        },
        success: function () {
            $(`#comment-${commentId}`).remove();
        },
        error: function () {
            alert("댓글 삭제 실패");
        }
    });
});


// 답글 달기 버튼 눌렀을 때 입력창 보여주기
$(document).on("click", ".reply-btn", function () {
    const parentId = $(this).data("parent-id");
    $(`#reply-form-${parentId}`).toggleClass("d-none");
});

// 답글 등록 처리
$(document).on("click", ".submit-reply", function () {
    const $form = $(this).closest(".reply-form");
    const parentId = $form.attr("id").split("-")[2];
    const content = $form.find(".reply-content").val();
    const postId = $("#commentPostID").val();
    const csrfToken = $("input[name='_csrf']").val();

    $.ajax({
        url: "/comment/reply",
        type: "POST",
        data: {
            postId: postId,
            parentId: parentId,
            content: content,
            _csrf: csrfToken
        },
        success: function () {
            const replyHtml = `
                <div class="ms-4 border-start ps-2 mt-2">
                    <img src="/img/default-profile.png" class="rounded-circle me-2" style="width:30px; height:30px;">
                    <strong>나</strong>
                    <div>${content}</div>
                    <div class="text-muted">${new Date().toLocaleString()}</div>
                    <hr>
                </div>`;
            $(`#reply-form-${parentId}`).before(replyHtml);
            $form.find(".reply-content").val("");
            $form.addClass("d-none");
        },
        error: function () {
            alert("답글 등록 실패");
        }
    });
});

$(document).on("click", ".like-btn", function () {
    const $btn = $(this);
    const postId = $btn.data("post-id");

    $.ajax({
        url: `/post/vote/${postId}`,
        type: "GET",
        headers: {
            "X-Requested-With": "XMLHttpRequest"
        },
        success: function () {
            // 현재 하트 상태 읽기
            const liked = $btn.data("liked");
            let count = parseInt($btn.data("like-count"));

            // 상태 토글
            const newLiked = !liked;
            const newCount = newLiked ? count + 1 : count - 1;

            // 상태 업데이트
            $btn.data("liked", newLiked);
            $btn.data("like-count", newCount);

            // 텍스트 업데이트
            $btn.html(`${newLiked ? "🧡" : "🤍"} ${newCount}`);
        },
        error: function () {
            alert("좋아요 처리 실패!");
        }
    });
});