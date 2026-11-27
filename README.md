# IVE PLAY — protótipo Android v0.1

**Identidade:** IVE = Imagem, Vídeo e Entretenimento. Aplicativo próprio, sem nome ou código do 9Xtream.

## Incluído
- Tela inicial com a marca IVE e navegação por controle remoto ou toque.
- Importação de canais por URL M3U (EXTINF, group-title).
- Conexão Xtream Codes (`player_api.php`, `get_live_streams`) para canais ao vivo.
- Busca de canais/categorias e reprodução com Media3 ExoPlayer (HLS e formatos suportados).
- Mesmo pacote Android para celular, Android TV e TV Box Android compatíveis.

## Ainda não incluído
- Catálogo VOD de filmes/séries, favoritos persistentes, EPG, logos por canal, perfil de clientes, login salvo, animação da abertura, layouts específicos para TV e celular.
- Não há APK compilado ou testado; este é o código-fonte inicial.
- Algumas listas Xtream usam formatos de stream diferentes de `.ts` e precisarão de adaptação.

## Gerar APK sem computador (GitHub Actions)
1. Crie um repositório privado no GitHub e envie **o conteúdo desta pasta** (não compartilhe credenciais ou listas de clientes).
2. Abra a aba **Actions**, escolha **Build Android APK** e clique **Run workflow**.
3. Aguarde a conclusão e baixe o artefato `IVE-PLAY-debug-APK`.
4. O APK debug serve para testes, não para publicação comercial. Para distribuição, configure assinatura release, privacidade, políticas e testes.

**Observações:** O app só deve acessar conteúdo para o qual o operador/cliente tenha autorização. Não há listas, senhas ou conteúdo embutidos. URLs HTTP são aceitas para compatibilidade com alguns servidores, mas HTTPS é recomendado. URLs e senhas Xtream são passadas ao servidor e devem ser tratadas como confidenciais. Em versão de produção, implementar armazenamento seguro, política de privacidade, gerenciamento de erros e controle de acesso.
