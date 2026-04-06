/**
 * ChatAssist AI Server
 * 可选组件：如果不想在手机上存储API密钥，可以部署此服务
 * 
 * 用法:
 *   node index.js
 * 
 * API端点:
 *   POST /api/reply - 生成回复建议
 */

const http = require('http');
const https = require('https');

// 配置
const PORT = 3000;
const ALLOWED_ORIGINS = ['*']; // 生产环境应限制

// 存储API密钥（建议使用环境变量）
const API_CONFIG = {
    minimax: {
        endpoint: process.env.MINIMAX_ENDPOINT || 'https://api.minimax.chat/v1/text/chatcompletion_v2',
        apiKey: process.env.MINIMAX_API_KEY || '',
        model: 'MiniMax-Text-01'
    },
    openai: {
        endpoint: process.env.OPENAI_ENDPOINT || 'https://api.openai.com/v1/chat/completions',
        apiKey: process.env.OPENAI_API_KEY || '',
        model: 'gpt-4o-mini'
    }
};

// 系统提示词
const SYSTEM_PROMPT = `你是一个智能聊天助手，名为Lisa。你的任务是根据聊天上下文，生成3个合适的回复建议。

要求：
1. 回复要自然、符合对话情境
2. 考虑不同的回复风格（正式、随意、幽默等）
3. 回复长度适中（5-30字）
4. 如果是多轮对话，注意上下文连贯性

请生成3个不同风格的回复建议，每个回复需要包含：
- text: 回复文本
- confidence: 置信度(0-1)
- style: 风格(normal/casual/polite/emoji/short/humor)

以JSON数组格式返回，例如：
[
  {"text": "好的，我知道了", "confidence": 0.9, "style": "normal"},
  {"text": "没问题~", "confidence": 0.8, "style": "casual"},
  {"text": "哈哈，这个有趣", "confidence": 0.7, "style": "humor"}
]`;

const server = http.createServer(async (req, res) => {
    // CORS头
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    
    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    // 路由
    if (req.method === 'POST' && req.url === '/api/reply') {
        try {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', async () => {
                const { context, provider = 'minimax', maxTokens = 200 } = JSON.parse(body);
                
                if (!context) {
                    res.writeHead(400, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'context is required' }));
                    return;
                }

                const result = await callAI(context, provider, maxTokens);
                
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify(result));
            });
        } catch (error) {
            console.error('Error:', error);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: error.message }));
        }
    } else {
        res.writeHead(404);
        res.end('Not Found');
    }
});

async function callAI(context, provider, maxTokens) {
    const config = API_CONFIG[provider];
    
    if (!config || !config.apiKey) {
        throw new Error(`Provider ${provider} not configured`);
    }

    const messages = [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: `聊天上下文：\n${context}\n\n请生成回复建议：` }
    ];

    const requestBody = {
        model: config.model,
        messages,
        max_tokens: maxTokens,
        temperature: 0.7
    };

    return new Promise((resolve, reject) => {
        const url = new URL(config.endpoint);
        const options = {
            hostname: url.hostname,
            port: url.port || (url.protocol === 'https:' ? 443 : 80),
            path: url.pathname,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${config.apiKey}`
            }
        };

        const protocol = url.protocol === 'https:' ? https : http;
        
        const req = protocol.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    
                    // 解析不同平台的响应格式
                    let content = '';
                    
                    if (provider === 'openai' || provider === 'minimax') {
                        content = json.choices?.[0]?.message?.content || '';
                    } else if (provider === 'claude') {
                        content = json.content?.[0]?.text || '';
                    }
                    
                    // 解析JSON数组
                    const suggestions = parseSuggestions(content);
                    resolve({ suggestions });
                    
                } catch (e) {
                    reject(new Error(`Failed to parse response: ${data}`));
                }
            });
        });

        req.on('error', reject);
        req.write(JSON.stringify(requestBody));
        req.end();
    });
}

function parseSuggestions(text) {
    // 尝试提取JSON数组
    const jsonStr = extractJsonArray(text);
    
    if (!jsonStr) return [];
    
    try {
        const arr = JSON.parse(jsonStr);
        return arr.map((item, index) => ({
            id: `suggestion_${index}`,
            text: item.text || '',
            confidence: item.confidence || 0.5,
            style: item.style || 'normal'
        })).filter(s => s.text);
    } catch (e) {
        console.error('Parse error:', e);
        return [];
    }
}

function extractJsonArray(text) {
    // 尝试 ```json ... ```
    const codeBlockMatch = text.match(/```(?:json)?\s*(\[[\s\S]*?\])\s*```/);
    if (codeBlockMatch) return codeBlockMatch[1];

    // 尝试直接找 []
    const firstBracket = text.indexOf('[');
    const lastBracket = text.lastIndexOf(']');
    if (firstBracket >= 0 && lastBracket > firstBracket) {
        return text.substring(firstBracket, lastBracket + 1);
    }

    return '';
}

server.listen(PORT, () => {
    console.log(`🤖 ChatAssist AI Server running on http://localhost:${PORT}`);
    console.log(`📡 API endpoint: POST /api/reply`);
    console.log('');
    console.log('Environment variables:');
    console.log('  MINIMAX_API_KEY / OPENAI_API_KEY');
    console.log('  MINIMAX_ENDPOINT / OPENAI_ENDPOINT');
});
