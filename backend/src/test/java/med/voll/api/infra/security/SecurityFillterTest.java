package med.voll.api.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import med.voll.api.domain.medico.Medico;
import med.voll.api.domain.usuario.Perfil;
import med.voll.api.domain.usuario.Usuario;
import med.voll.api.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SecurityFillterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private FilterChain filterChain;

    private SecurityFillter securityFillter;

    @BeforeEach
    void setUp() {
        securityFillter = new SecurityFillter(tokenService, usuarioRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /auth/login deve continuar sem token")
    void loginSemTokenDeveContinuarCadeia() throws Exception {
        var request = request("POST", "/auth/login");
        var response = new MockHttpServletResponse();

        securityFillter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenService, usuarioRepository);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    @DisplayName("POST /auth/login deve ignorar bearer antigo e continuar cadeia")
    void loginComBearerAntigoDeveIgnorarFiltroJwt() throws Exception {
        var request = request("POST", "/auth/login");
        request.addHeader("Authorization", "Bearer token-expirado");
        var response = new MockHttpServletResponse();

        securityFillter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenService, usuarioRepository);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    @DisplayName("POST /auth/cadastro deve continuar protegido por bearer inválido")
    void cadastroComBearerInvalidoDeveRetornar401() throws Exception {
        var request = request("POST", "/auth/cadastro");
        request.addHeader("Authorization", "Bearer token-expirado");
        var response = new MockHttpServletResponse();
        when(tokenService.getSubject("token-expirado")).thenThrow(new RuntimeException("Token expirado"));

        securityFillter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("{\"erro\":\"Token inválido ou expirado\"}", response.getContentAsString());
    }

    @Test
    @DisplayName("Token válido de médico inativo deve retornar 401 e bloquear cadeia")
    void tokenDeMedicoInativoDeveRetornar401() throws Exception {
        var medicoInativo = mock(Medico.class);
        when(medicoInativo.isAtivo()).thenReturn(false);
        var usuarioMedico = new Usuario(3L, "medico@test.com", "senha", Perfil.ROLE_MEDICO, medicoInativo);

        var request = request("GET", "/consultas");
        request.addHeader("Authorization", "Bearer token-medico-inativo");
        var response = new MockHttpServletResponse();

        when(tokenService.getSubject("token-medico-inativo")).thenReturn("medico@test.com");
        when(usuarioRepository.findByLogin("medico@test.com")).thenReturn(usuarioMedico);

        securityFillter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("{\"erro\":\"Conta de usuário inativa\"}", response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
