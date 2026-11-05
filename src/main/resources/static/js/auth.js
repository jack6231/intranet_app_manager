/**
 * 登录相关的页面公共逻辑（依赖 jQuery，页面需引入 fragments/auth :: meta）
 *  - 所有 jQuery ajax 请求带上 CSRF token：删除、清理、上传 App Store 这几个接口会校验
 *  - 未登录（401）跳登录页，登录后回到当前页
 *  - 会话过期（403：页面上的 token 已失效，CSRF 校验先于登录校验）提示后刷新页面；
 *    勾了「30 天内自动登录」的刷新后自动恢复登录，没勾的刷新后显示「登录」入口
 */
(function () {
    var token = $("meta[name='_csrf']").attr("content");
    var header = $("meta[name='_csrf_header']").attr("content");
    if (token && header) {
        var headers = {};
        headers[header] = token;
        $.ajaxSetup({headers: headers});
    }

    $(document).ajaxError(function (event, xhr) {
        if (xhr.status === 401) {
            window.location.href = loginURL();
        } else if (xhr.status === 403) {
            alert("页面已过期，将刷新页面，请刷新后重新操作");
            window.location.reload();
        }
    });
})();

/** 当前是否已登录（服务端渲染时给 body 加 logged-in） */
function isLoggedIn() {
    return $("body").hasClass("logged-in");
}

/** 登录页地址，登录后回到当前页 */
function loginURL() {
    return "/login?next=" + encodeURIComponent(window.location.pathname);
}
