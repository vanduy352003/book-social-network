package com.duy.book.auth;

import com.duy.book.email.EmailService;
import com.duy.book.email.EmailTemplateName;
import com.duy.book.exception.OperationNotPermittedException;
import com.duy.book.role.RoleRepository;
import com.duy.book.security.JwtService;
import com.duy.book.user.Token;
import com.duy.book.user.TokenRepository;
import com.duy.book.user.User;
import com.duy.book.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    @Value("${application.mailing.frontend.activation-url}")
    private String activationUrl;
    @Value("${application.google.client-id}")
    private String googleClientId;
    @Value("${application.google.redirect-uri}")
    private String googleRedirectURI;
    @Value("${application.google.client-secret}")
    private String googleSecret;
    @Value("${application.google.scope}")
    private List<String> googleScope;
    @Value("${application.facebook.client-id}")
    private String facebookClientId;
    @Value("${application.facebook.client-secret}")
    private String facebookSecret;
    @Value("${application.facebook.redirect-uri}")
    private String facebookRedirectURI;
    @Value("${application.facebook.login-url}")
    private String facebookLoginUrl;


    public void register(RegistrationRequest request) throws MessagingException {
        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role USER not found"));
        var user = User.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .accountLocked(false)
                .enabled(false)
                .roles(List.of(userRole))
                .build();
        userRepository.save(user);
        sendValidationEmail(user);
    }

    private void sendValidationEmail(User user) throws MessagingException {
        var newToken = generateAndSaveActivationToken(user);
        //send email
        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                activationUrl,
                newToken,
                "Account activation"
        );
    }

    private String generateAndSaveActivationToken(User user) {
        String generatedToken = generateActivationCode(6);
        var token = Token.builder()
                .token(generatedToken)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .user(user)
                .build();
        tokenRepository.save(token);
        return generatedToken;
    }

    private String generateActivationCode(int length) {
        String characters = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(characters.length());
            codeBuilder.append(characters.charAt(randomIndex));
        }
        return codeBuilder.toString();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var claims = new HashMap<String, Object>();
        var user = ((User)auth.getPrincipal());
        claims.put("fullname", user.fullName());
        var jwtToken = jwtService.generateToken(claims, user);
        return AuthenticationResponse.builder()
                .token(jwtToken).build();
    }

//    @Transactional
    public void activateAccount(String token) throws MessagingException {
        Token savedToken = tokenRepository.findByToken(token)
                // todo exception has to be defined
                .orElseThrow(()-> new RuntimeException("Invalid token"));
        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            sendValidationEmail(savedToken.getUser());
            throw new RuntimeException("Activation token has expired. A new token has been sent to the same email address");
        }
        var user = userRepository.findById(savedToken.getUser().getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        user.setEnabled(true);
        userRepository.save(user);
        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);
    }


    public AuthenticationResponse authenticateWithGoogleIdToken(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken =  verifier.verify(idTokenString);
        if (idToken == null) {
            throw new OperationNotPermittedException("No id token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String userId = payload.getSubject();
        System.out.println("User ID: " + userId);
        System.out.println("Payload " + payload);

        String email = payload.getEmail();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            var userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new IllegalStateException("Role USER not found"));
            var newUser = User.builder()
                    .firstname((String) payload.get("given_name"))
                    .lastname((String) payload.get("family_name"))
                    .email(email)
                    .accountLocked(false)
                    .enabled(true)
                    .roles(List.of(userRole))
                    .build();
            user = userRepository.save(newUser);
        }

        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var claims = new HashMap<String, Object>();
        claims.put("fullname", user.fullName());
        var jwtToken = jwtService.generateToken(claims, user);

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public SocialLoginResponse getSocialLoginLink(String socialName) throws BadRequestException {
        String url = null;
        if (socialName.equalsIgnoreCase("google")) {
            url = createGoogleUrl();
        } else if (socialName.equalsIgnoreCase("facebook")) {
            url = createFacebookUrl();
        }
        if (Objects.isNull(url)){
            throw new BadRequestException("Not valid social name");
        }
        return SocialLoginResponse.builder().url(url).build();
    }

    private String createGoogleUrl() {
        return new GoogleAuthorizationCodeRequestUrl(
                googleClientId,
                googleRedirectURI,
                googleScope
        ).setState(generateState()).build();
    }

    private String createFacebookUrl() {
        StringBuilder url = new StringBuilder();
        url.append(facebookLoginUrl);
        url.append("?client_id=").append(facebookClientId);
        url.append("&redirect_uri=").append(facebookRedirectURI);
        url.append("&scope=email,public_profile");
        url.append("&state=").append(generateState());
        return url.toString();
    }

    private String generateState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    //TODO update the database to add tables relate to social login
    // maybe use the id provide form the provider
    //TODO facebook it self currently have a bug
    // that if an account change email it will not be able to retrieve their email
    public String authenticateWithFacebook(String code) throws JsonProcessingException {
        String token = "";
        StringBuilder accessTokenUrl = new StringBuilder();
        accessTokenUrl.append("https://graph.facebook.com/v21.0/oauth/access_token");
        accessTokenUrl.append("?client_id=").append(facebookClientId);
        accessTokenUrl.append("&redirect_uri=").append(facebookRedirectURI);
        accessTokenUrl.append("&client_secret=").append(facebookSecret);
        accessTokenUrl.append("&code=").append(code);

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> accessTokenResp = new ObjectMapper().readValue(
                restTemplate.getForEntity(accessTokenUrl.toString(), String.class).getBody(),
                new TypeReference<Map<String, Object>>() {}
        );
        log.info(accessTokenResp.toString());

        String debugTokenUrl = "https://graph.facebook.com/debug_token" +
                "?input_token=" + accessTokenResp.get("access_token") +
                "&access_token=" + accessTokenResp.get("access_token"); // Use app_access_token (app_id|app_secret)

        ResponseEntity<String> response = restTemplate.getForEntity(debugTokenUrl, String.class);
        log.info("Token Debug Response: {}", response.getBody());


        StringBuilder requestInfoUrl = new StringBuilder();
        requestInfoUrl.append("https://graph.facebook.com/v21.0/me");
        requestInfoUrl.append("?access_token=").append(accessTokenResp.get("access_token"));
        requestInfoUrl.append("&fields=id,first_name,last_name,email,picture");
        Map<String, Object> info = new ObjectMapper().readValue(
                restTemplate.getForEntity(requestInfoUrl.toString(), String.class).getBody(),
                new TypeReference<Map<String, Object>>() {}
        );
        log.info(info.toString());
//        User user = userRepository.findByEmail(info.get("email").toString()).orElse(null);
//
//        if (user == null) {
//            var userRole = roleRepository.findByName("USER")
//                    .orElseThrow(() -> new IllegalStateException("Role USER not found"));
//            var newUser = User.builder()
//                    .firstname((String) info.get("first_name"))
//                    .lastname((String) info.get("last_name"))
//                    .email(info.get("email").toString())
//                    .accountLocked(false)
//                    .enabled(true)
//                    .roles(List.of(userRole))
//                    .build();
//            user = userRepository.save(newUser);
//        }
//
//        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
//        SecurityContextHolder.getContext().setAuthentication(auth);
//
//        var claims = new HashMap<String, Object>();
//        claims.put("fullname", user.fullName());
//        token = jwtService.generateToken(claims, user);

        return token;
    }

    public String authenticateWithGoogle(String code) throws IOException {
        String token = "";
        String accessToken = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(), new GsonFactory(),
                googleClientId, googleSecret, code, googleRedirectURI
        ).execute().getAccessToken();

        RestTemplate restTemplate = new RestTemplate();

        restTemplate.getInterceptors().add((req, body, executionContext) -> {
            req.getHeaders().set("Authorization", "Bearer " + accessToken);
            return executionContext.execute(req, body);
        });
        String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
        Map<String, Object> info = new ObjectMapper().readValue(
                restTemplate.getForEntity(userInfoUrl, String.class).getBody(),
                new TypeReference<Map<String, Object>>() {}
        );

        User user = userRepository.findByEmail(info.get("email").toString()).orElse(null);

        if (user == null) {
            var userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new IllegalStateException("Role USER not found"));
            var newUser = User.builder()
                    .firstname((String) info.get("given_name"))
                    .lastname((String) info.get("name"))
                    .email(info.get("email").toString())
                    .accountLocked(false)
                    .enabled(true)
                    .roles(List.of(userRole))
                    .build();
            user = userRepository.save(newUser);
        }

        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var claims = new HashMap<String, Object>();
        claims.put("fullname", user.fullName());
        token = jwtService.generateToken(claims, user);

        return token;
    }

    public AuthenticationResponse authenticateSocial(SocialLoginRequest request) throws IOException {
        String token = switch (request.provider()) {
            case "facebook" -> authenticateWithFacebook(request.code());
            case "google" -> authenticateWithGoogle(request.code());
            default -> throw new IllegalArgumentException("Unsupported provider");
        };
        //log.info(request.toString());
        return AuthenticationResponse.builder().token(token).build();
    }
}
