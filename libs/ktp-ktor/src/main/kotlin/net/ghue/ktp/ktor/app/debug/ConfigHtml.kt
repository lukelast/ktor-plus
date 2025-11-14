package net.ghue.ktp.ktor.app.debug

// language=HTML
internal const val CONFIG_TEMPLATE =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>App Configuration</title>
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
            h2 {
                font-size: 20px;
                color: #333333;
                margin: 32px 0 8px;
            }
            .table-section:first-of-type h2 {
                margin-top: 0;
            }
            .table-section {
                margin-bottom: 32px;
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
            }
            th {
                background-color: #009879;
                color: #ffffff;
            }
            tr:hover {
                background-color: #f1f1f1;
            }
            th.path-col, td.path-cell {
                width: 22%;
            }
            th.value-col, td.value-cell {
                width: 58%;
            }
            th.source-col, td.source-cell {
                width: 20%;
            }
        </style>
    </head>
    <body>
        <h1>App Configuration</h1>
        <div class="table-section">
            <h2>KTP Config Records</h2>
            <table>
                <thead>
                    <tr>
                        <th class="path-col">Path</th>
                        <th class="value-col">Value</th>
                        <th class="source-col">Source</th>
                    </tr>
                </thead>
                <tbody>
                    {{CONFIG_ROWS}}
                </tbody>
            </table>
        </div>
        <div class="table-section">
            <h2>Runtime Info</h2>
            <table>
                <thead>
                    <tr>
                        <th class="path-col">Path</th>
                        <th class="value-col">Value</th>
                    </tr>
                </thead>
                <tbody>
                    {{RUNTIME_ROWS}}
                </tbody>
            </table>
        </div>
        <div class="table-section">
            <h2>Environment Variables</h2>
            <table>
                <thead>
                    <tr>
                        <th class="path-col">Path</th>
                        <th class="value-col">Value</th>
                    </tr>
                </thead>
                <tbody>
                    {{ENVIRONMENT_ROWS}}
                </tbody>
            </table>
        </div>
        <div class="table-section">
            <h2>System Properties</h2>
            <table>
                <thead>
                    <tr>
                        <th class="path-col">Path</th>
                        <th class="value-col">Value</th>
                    </tr>
                </thead>
                <tbody>
                    {{SYSTEM_ROWS}}
                </tbody>
            </table>
        </div>
    </body>
    </html>
    """
