package com.clubfund.controller;

import com.clubfund.entity.User;
import com.clubfund.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 登录注册控制器：处理登录页、注册页、退出登录。
 * Controller 层负责"接请求、调 Service、回页面"。
 */
@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    /** 打开登录页 */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /** 提交登录表单 */
    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request, HttpSession session,
                          Model model) {
        User user = userService.login(username, password);
        if (user == null) {
            model.addAttribute("error", "用户名或密码错误");
            return "login";
        }
        request.changeSessionId();               // 登录成功后更换编号，保留正常会话状态
        session.setAttribute("loginUser", user);   // 登录成功，用户信息存进 session
        return "redirect:/index";                  // 跳转到首页
    }

    /** 打开注册页 */
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    /** 提交注册表单 */
    @PostMapping("/register")
    public String doRegister(@RequestParam(required = false) String username,
                             @RequestParam(required = false) String password,
                             @RequestParam(required = false) String realName,
                             Model model, RedirectAttributes ra) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setRealName(realName);
        String error = userService.register(user);
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("username", username);
            model.addAttribute("realName", realName);
            return "register";
        }
        ra.addFlashAttribute("success", "注册成功，请登录");
        return "redirect:/login";                  // 注册成功，去登录
    }

    /** CSRF 校验失败时由过滤器转发，保留 403 状态。 */
    @RequestMapping("/csrf-error")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String csrfError() {
        return "csrf_error";
    }

    /** 退出登录：使整个会话失效，回登录页 */
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
