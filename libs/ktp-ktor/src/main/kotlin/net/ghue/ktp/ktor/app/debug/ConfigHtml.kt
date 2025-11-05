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
            table {
                border-collapse: collapse;
                width: 100%;
                max-width: 1600px;
                margin: 16px 0;
                font-size: 14px;
                text-align: left;
                background-color: #ffffff;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
            }
            th, td {
                padding: 8px 16px;
                border-bottom: 1px solid #dddddd;
                word-break: break-word;
            }
            th {
                background-color: #009879;
                color: #ffffff;
            }
            tr:hover {
                background-color: #f1f1f1;
            }
            caption {
                caption-side: top;
                font-size: 24px;
                font-weight: bold;
                padding: 8px;
            }
        </style>
    </head>
    <body>
        <table>
            <thead>
                <tr>
                    <th>Path</th>
                    <th>Value</th>
                    <th>Source</th>
                </tr>
            </thead>
            <tbody>
                {{TABLE}}
            </tbody>
        </table>
    </body>
    </html>
    """
