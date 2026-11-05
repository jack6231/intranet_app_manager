/**
 * 管理页：删除账号（依赖 auth.js 给 ajax 加 CSRF token）
 */
$(document).on("click", ".admin-delete-account", function () {
    var button = $(this);
    var name = button.attr("data-name");
    if (!confirm("确定删除账号「" + name + "」?\n删除后该账号立即退出登录，已有的操作记录保留")) {
        return;
    }
    button.attr("disabled", "disabled");
    $.post("/admin/accounts/" + encodeURIComponent(button.attr("data-id")) + "/delete", function (result) {
        if (result.success) {
            window.location.reload();
        } else {
            alert(result.message || "删除失败");
            button.removeAttr("disabled");
        }
    });
});
