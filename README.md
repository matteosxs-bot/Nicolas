# Entre Noses

Aplicativo Android de finanças pessoais e compartilhadas para duas pessoas. Interface em português brasileiro, valores em reais e datas DD/MM/AAAA.

## O que já está implementado no código
- Movimentos de entrada e saída, com datas e situação de pagamento.
- Metas, depósitos, lista de compras, contas recorrentes, limites e relatórios.
- Login individual por e-mail, criação de conta e solicitação de recuperação de senha via Supabase Auth.
- Sessão e dados locais cifrados com Android Keystore / EncryptedSharedPreferences.
- Separação dos dados locais por ID da conta autenticada.
- Esquema SQL inicial para contas, membros da casa, dados privados e dados compartilhados com Row Level Security (RLS).

## Configuração necessária para autenticação e nuvem
A autenticação real fica desativada enquanto o backend não estiver configurado. Não insira senha real no app de teste até aparecer um servidor configurado.

1. Crie um projeto Supabase sob controle do casal.
2. No painel SQL Editor, execute `supabase/schema.sql`.
3. Em GitHub → Settings → Secrets and variables → Actions, cadastre:
   - `SUPABASE_URL`: URL HTTPS do projeto.
   - `SUPABASE_ANON_KEY`: chave pública/anon do projeto. Nunca use a `service_role` no APK.
4. Execute novamente o workflow **Android APK**.

O URL e a chave pública são injetados durante a compilação por secrets do GitHub Actions. Não grave chaves privadas no código nem no APK.

## Importante sobre o estado da nuvem
O login, cadastro, recuperação de senha, armazenamento local cifrado, criação do espaço do casal e entrada por código de convite estão no código. A sincronização automática dos movimentos e módulos e os testes reais de isolamento entre duas contas ainda precisam ser conectados e validados com um projeto Supabase ativo. Não considere uma build bem-sucedida como prova de que a nuvem está funcionando.

## Build e testes
O GitHub Actions executa `gradle assembleDebug` a cada alteração em `main` e publica um APK de debug como artefato. Isso confirma compilação, não substitui testes de uso em dois aparelhos.
