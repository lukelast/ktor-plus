package net.ghue.ktp.ktor.app.debug

// language=HTML
internal const val ERRORS_TEMPLATE =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Error Triggers</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                margin: 32px;
                background-color: #f4f4f4;
            }
            h1 {
                font-size: 32px;
                color: #222222;
                margin-bottom: 8px;
            }
            p {
                margin: 16px 0;
                color: #555555;
                font-size: 14px;
            }
            button {
                font: inherit;
                padding: 6px 12px;
                border: 1px solid #009879;
                border-radius: 4px;
                background-color: #ffffff;
                color: #009879;
                cursor: pointer;
                white-space: nowrap;
            }
            button:hover {
                background-color: #009879;
                color: #ffffff;
            }
            table {
                border-collapse: collapse;
                width: 100%;
                max-width: 1600px;
                margin: 16px 0;
                font-size: 14px;
                text-align: left;
                background-color: #ffffff;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                table-layout: fixed;
            }
            th, td {
                padding: 8px 16px;
                border-bottom: 1px solid #dddddd;
                word-break: break-word;
                overflow-wrap: anywhere;
                vertical-align: top;
            }
            th {
                background-color: #009879;
                color: #ffffff;
            }
            tr:hover {
                background-color: #f1f1f1;
            }
            .case-cell {
                width: 16%;
            }
            .description-cell {
                width: 34%;
            }
            .expected-cell {
                width: 8%;
                text-align: center;
            }
            .result-cell {
                width: 42%;
            }
            pre {
                margin: 0;
                white-space: pre-wrap;
                font-size: 12px;
            }
            pre.match {
                color: #155724;
            }
            pre.mismatch {
                color: #721c24;
            }
        </style>
    </head>
    <body>
        <h1>Error Triggers</h1>
        <p>
            Each button sends a request that fails in a different way. Compare the response shown
            here with the log entries the server wrote for it.
            <button type="button" id="run-all">Run all</button>
        </p>
        <table>
            <thead>
                <tr>
                    <th class="case-cell">Request</th>
                    <th class="description-cell">Description</th>
                    <th class="expected-cell">Expected</th>
                    <th class="result-cell">Response</th>
                </tr>
            </thead>
            <tbody>
                {{CASE_ROWS}}
            </tbody>
        </table>
        <script>
            const base = location.pathname.replace(/\/+$/, '');

            async function run(button) {
                const id = button.dataset.id;
                const result = document.getElementById('result-' + id);
                result.className = '';
                result.textContent = 'Running...';
                const options = { method: button.dataset.method };
                if (button.dataset.contentType) {
                    options.headers = { 'Content-Type': button.dataset.contentType };
                    options.body = button.dataset.body;
                }
                try {
                    const response = await fetch(base + '/' + id, options);
                    const text = await response.text();
                    result.className =
                        String(response.status) === button.dataset.expected ? 'match' : 'mismatch';
                    result.textContent = response.status + ' ' + response.statusText + '\n' + text;
                } catch (error) {
                    result.className = 'mismatch';
                    result.textContent = 'Request failed: ' + error;
                }
            }

            document.querySelectorAll('button[data-id]').forEach((button) => {
                button.addEventListener('click', () => run(button));
            });
            document.getElementById('run-all').addEventListener('click', async () => {
                for (const button of document.querySelectorAll('button[data-id]')) {
                    await run(button);
                }
            });
        </script>
    </body>
    </html>
    """
