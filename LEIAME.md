# Rastreio – app do celular do vendedor externo

App Android (Android 8 ou superior) que roda como **Device Owner** no moto e6 plus e:

- registra a localização a cada **5 minutos**, de **segunda a sábado, das 7h às 19h** (expediente 8h–18h com 1h de margem);
- às **19h05** envia por e-mail o relatório do dia: resumo no corpo, planilha **.csv** (Excel) e trajeto **.kml** (Google Earth ou Google My Maps);
- trava localização, rede móvel, modo avião, reset de fábrica, modo de segurança, data/hora e depuração USB;
- oculta todos os apps com ícone que não estão na lista de mantidos (inclusive apps instalados depois);
- registra no relatório quando o aparelho foi desligado/ligado, GPS ou dados desligados, e localização simulada;
- guarda 30 dias no aparelho e reenvia relatórios que falharam (tenta de hora em hora).

Uma notificação fixa "Registro de expediente ativo" fica visível durante o horário de registro.

## 1. Conta de e-mail de envio

Crie no Titan Mail (HostGator) uma caixa só para isso, por exemplo `rastreio@suaempresa.com.br`.
Servidor: `smtp.titan.email`, porta 465, SSL (já configurado no app).

## 2. Gerar o APK no GitHub (sem instalar nada no PC)

1. Crie uma conta em github.com e um repositório **privado** (ex.: `rastreio`).
2. Envie todos os arquivos desta pasta para o repositório (botão "Add file › Upload files"; arraste a pasta inteira, incluindo `.github`).
3. Em **Settings › Secrets and variables › Actions › New repository secret**, crie:

| Nome | Valor |
| --- | --- |
| `KEYSTORE_BASE64` | conteúdo do arquivo `rastreio.jks.base64.txt` |
| `KEYSTORE_PASSWORD` | senha em `senha-da-chave.txt` |
| `SMTP_USER` | e-mail de envio |
| `SMTP_PASSWORD` | senha desse e-mail |
| `MAIL_TO` | quem recebe (vários: separe por vírgula) |
| `ADMIN_PIN` | PIN numérico do painel do administrador |

   Opcional, na aba **Variables**: `DEVICE_LABEL` = nome que aparece no relatório (padrão "Vendedor 01").
4. Aba **Actions › Gerar APK › Run workflow**. Em uns 5 minutos, o APK aparece em "Artifacts" (`rastreio-apk`).

**Guarde `rastreio.jks` e a senha.** Toda atualização do app precisa ser assinada com essa mesma chave.

## 3. Instalar no celular

1. Faça backup do que for importante no aparelho.
2. Remova todas as contas (Configurações › Contas) ou faça o reset de fábrica. O Android só aceita Device Owner sem contas.
3. Ative as Opções do desenvolvedor (7 toques em "Número da versão") e a **Depuração USB**.
4. No PC (Windows), baixe o Android Platform Tools e coloque na mesma pasta: `adb.exe`, `rastreio.apk` (o baixado do GitHub, renomeado) e `instalar.bat`.
5. Conecte o celular, aceite o aviso de depuração e rode `instalar.bat`.
6. Depois disso, adicione a conta Google e configure o WhatsApp normalmente.

## 4. Painel do administrador

Toque no ícone **Rastreio** na tela inicial (ou disque `*#*#7278#*#*` no app Telefone) e informe o PIN. Sem o PIN, o ícone não faz nada. **O `ADMIN_PIN` é obrigatório**: sem ele, o painel fica trancado. Lá você pode:

- enviar um relatório de teste do dia;
- reenviar pendentes;
- reaplicar os bloqueios / mostrar todos os apps;
- liberar os bloqueios para manutenção;
- remover o gerenciamento (para desinstalar o app).

## 5. Testes antes de entregar ao vendedor

- [ ] No painel, "Enviar relatório de hoje (teste)" chega no e-mail.
- [ ] Apps fora da lista sumiram da tela; os da lista funcionam.
- [ ] Nos atalhos rápidos, localização e modo avião não podem ser alterados.
- [ ] Tentar desligar os dados móveis pelos atalhos rápidos (veja limitações).
- [ ] Deixar o celular um dia inteiro em uso e conferir o relatório das 19h05 e a bateria.

## Limitações conhecidas

- Ninguém consegue impedir que o aparelho seja **desligado** ou fique sem sinal; isso aparece no relatório como ocorrência ou intervalo sem registro.
- O Android 9 não tem uma API pública para **forçar os dados móveis ligados**. O app trava as configurações de rede, mas o atalho rápido de dados pode continuar funcionando em alguns aparelhos. Se acontecer, fica registrado no relatório. Se for necessário bloquear, ative `LOCK_STATUS_BAR` em `Config.kt` (isso também esconde as notificações).
- Os pontos ficam no celular até o envio: se o aparelho for perdido antes das 19h05, perde-se o registro daquele dia.

## Ajustes comuns (arquivo `app/src/main/java/br/com/hasolucoes/rastreio/Config.kt`)

- Horário: `WINDOW_START_HOUR` / `WINDOW_END_HOUR`
- Dias: `WORK_DAYS`
- Intervalo: `INTERVAL_MINUTES`
- Apps mantidos: `KEEP_PACKAGES`

Depois de alterar, rode o workflow de novo e instale o APK novo por cima:

1. Painel › "Liberar bloqueios (manutenção)" (reativa a depuração USB).
2. Opções do desenvolvedor › Depuração USB ligada; no PC: `adb install -r rastreio.apk`.
3. Painel › "Aplicar bloqueios e ocultar apps".

Não é preciso reset: a atualização mantém o gerenciamento, desde que assinada com a mesma chave.

## LGPD

Localização é dado pessoal. Antes de entregar o aparelho, o vendedor deve assinar o termo de uso do celular corporativo, informando o registro de localização no horário de trabalho e o prazo de guarda.
