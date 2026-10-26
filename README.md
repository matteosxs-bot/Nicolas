# Entre Noses

Aplicativo Android de finanças pessoais e compartilhadas para duas pessoas. Interface em português brasileiro, valores em reais e datas DD/MM/AAAA.

## Funcionalidades presentes no código
- Movimentos de entrada/saída, datas manuais e estado pendente/pago.
- Metas, depósitos, lista de compras, contas recorrentes, limites por categoria, relatórios e exportação CSV.
- Lixeira com restauração e retenção de 30 dias.
- Login individual, criação de conta, recuperação de senha e renovação de sessão via Supabase Auth.
- Armazenamento local cifrado com Android Keystore / EncryptedSharedPreferences e separação por ID de conta.
- Modo local para usar o app antes de configurar a nuvem.
- Espaço do casal com convite de dois membros.
- Sincronização ao abrir o app e botão de sincronização manual: os dados financeiros são privados por conta; a lista de compras é compartilhada apenas dentro do mesmo espaço do casal.
- Histórico de abastecimento e manutenção de veículos.
- Controle manual de estoque de casa/pets com aviso visual de reposição.
- Dívidas, parcelas e assinaturas recorrentes.

## Configuração da nuvem
A autenticação e a sincronização online ficam desativadas até que um projeto Supabase seja conectado. Sem a configuração, o app pode ser usado no modo local; não tente entrar com uma senha real enquanto o servidor não estiver configurado.

1. Crie um projeto Supabase.
2. No painel SQL Editor, execute `supabase/schema.sql`.
3. Em GitHub → Settings → Secrets and variables → Actions, cadastre:
   - `SUPABASE_URL`: URL HTTPS do projeto.
   - `SUPABASE_ANON_KEY`: chave pública/anon do projeto. Nunca use a chave `service_role` no APK.
4. Gere uma nova build pelo workflow **Android APK**.

A sincronização automática tenta rodar ao abrir o app quando o backend está configurado. A sincronização manual também está disponível na seção **Mais → Espaço do casal e convite**.

## Limitações que ainda exigem validação
- O backend Supabase ainda não foi conectado a um projeto real nesta sessão. Portanto, cadastro, login, convite e sincronização não foram testados contra um servidor ativo nem em dois aparelhos.
- A sincronização trabalha com mesclagem de registros; a resolução de conflitos e exclusões em todos os módulos precisa de testes reais antes de confiar nela para dados financeiros importantes.
- A lista de compras é a primeira área compartilhada entre membros; os demais registros financeiros permanecem privados por conta. Ainda falta uma opção explícita para compartilhar um movimento financeiro individual.
- Notificações em segundo plano, bloqueio biométrico, contas bancárias/cartões de crédito completos, importação de extratos CSV/OFX/PDF com prévia e deduplicação, e testes automatizados abrangentes ainda não estão concluídos.

## Build
O GitHub Actions executa `gradle assembleDebug` e publica um APK de debug como artefato. Compilar com sucesso verifica a compilação, não substitui testes de uso real, segurança ou sincronização.
