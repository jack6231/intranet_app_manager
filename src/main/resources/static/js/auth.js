/**
 * 登录相关的页面公共逻辑（依赖 jQuery，页面需引入 fragments/auth :: meta 和 :: loginModal）
 *  - 所有 jQuery ajax 请求带上 CSRF token：删除、清理、上传 App Store 这几个接口会校验
 *  - requireLogin(action)：已登录直接执行；未登录弹出登录框，登录成功后接着执行 action
 *  - 服务端返回 401（未登录）弹登录框；403（会话过期，页面上的 token 已失效，CSRF 校验先于登录校验）
 *    提示后刷新页面：勾了「30 天内自动登录」的刷新后自动恢复登录
 */
(function () {
    setCsrfHeader($("meta[name='_csrf_header']").attr("content"), $("meta[name='_csrf']").attr("content"));

    $(document).ajaxError(function (event, xhr) {
        if (xhr.status === 401) {
            $("body").removeClass("logged-in");
            openLoginModal(null);
        } else if (xhr.status === 403) {
            alert("页面已过期，将刷新页面，请刷新后重新操作");
            window.location.reload();
        }
    });

    var $modal = $("#login-modal");
    $modal.on("click", "[data-login-close]", closeLoginModal);
    $(document).on("keydown", function (e) {
        if (e.key === "Escape" && $modal.hasClass("is-open")) {
            closeLoginModal();
        }
    });
    $("#login-modal-form").on("submit", function (e) {
        e.preventDefault();
        submitLoginModal(this);
    });
})();

var pendingLoginAction = null;

function setCsrfHeader(header, token) {
    if (header && token) {
        var headers = {};
        headers[header] = token;
        $.ajaxSetup({headers: headers});
    }
}

/** 当前是否已登录（服务端渲染时给 body 加 logged-in，弹窗登录成功后前端补上） */
function isLoggedIn() {
    return $("body").hasClass("logged-in");
}

/** 需要登录的操作统一走这里 */
function requireLogin(action) {
    if (isLoggedIn()) {
        action();
    } else {
        openLoginModal(action);
    }
}

function openLoginModal(action) {
    pendingLoginAction = action;
    var $modal = $("#login-modal");
    $modal.find(".login-modal-error").text("");
    $modal.addClass("is-open");
    $modal.find("input[name='username']").focus();
}

function closeLoginModal() {
    pendingLoginAction = null;
    $("#login-modal").removeClass("is-open");
}

function submitLoginModal(form) {
    var $form = $(form);
    var $button = $form.find("button[type='submit']");
    var $error = $("#login-modal .login-modal-error");
    $button.attr("disabled", "disabled");
    $error.text("");
    $.ajax({
        url: "/login",
        type: "POST",
        data: $form.serialize(),
        dataType: "json",
        global: false,   // 失败在弹窗里提示，不走页面的全局 401/403 处理
        success: function (result) {
            if (!result.success) {
                $error.text(result.message || "登录失败");
                return;
            }
            applyLogin(result);
            var action = pendingLoginAction;
            closeLoginModal();
            form.reset();
            if (action) {
                action();
            }
        },
        error: function (xhr) {
            $error.text(xhr.status === 403 ? "页面已过期，请刷新页面后重试" : "登录失败，请稍后重试");
        },
        complete: function () {
            $button.removeAttr("disabled");
        }
    });
}

/** 弹窗登录成功：换上新 token，导航栏改成「用户名 退出」，页面切到已登录状态 */
function applyLogin(result) {
    $("meta[name='_csrf']").attr("content", result.csrfToken);
    setCsrfHeader(result.csrfHeader, result.csrfToken);
    $("body").addClass("logged-in");
    var $logout = $('<form class="auth-logout" method="post" action="/logout"></form>')
        .append($('<input type="hidden"/>').attr({name: result.csrfParameter, value: result.csrfToken}))
        .append($('<input type="hidden" name="next"/>').val(window.location.pathname))
        .append('<button type="submit">退出</button>');
    var $bar = $(".auth-bar").empty();
    if (result.admin) {
        $bar.append('<a class="auth-admin" href="/admin">管理</a>');
    }
    $bar.append($('<span class="auth-user"></span>').text(result.username))
        .append($logout);
}
